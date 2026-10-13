package vn.group7.content;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.*;
import vn.group7.core.*;

@Component
public class Imports {
  private final Db db;
  private final ContentStore store;

  public Imports(Db d, ContentStore s) {
    db = d;
    store = s;
  }

  record Item(int location, Map<String, Object> fields) {}

  static final String NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

  static Map<String, Object> issue(
      Integer row, Integer paragraph, String field, String code, String message) {
    return Json.nullable(
        "row", row, "paragraph", paragraph, "field", field, "code", code, "message", message);
  }

  static Map<String, Object> outcome(
      String status,
      Integer total,
      Integer valid,
      Integer invalid,
      int count,
      List<?> ids,
      List<?> errors) {
    return Json.nullable(
        "status",
        status,
        "totalItems",
        total,
        "validCount",
        valid,
        "invalidCount",
        invalid,
        "createdCount",
        count,
        "createdIds",
        ids,
        "errors",
        errors);
  }

  static ApiError fileError(int status, String code, String message) {
    var i = issue(null, null, "file", code, message);
    return new ApiError(
        status,
        code,
        message,
        List.of(),
        Map.of("import", outcome("REJECTED", null, null, null, 0, List.of(), List.of(i))));
  }

  public static Map<String, byte[]> inspect(byte[] bytes) {
    var files = new LinkedHashMap<String, byte[]>();
    long expanded = 0;
    int count = 0;
    try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      ZipEntry e;
      while ((e = zip.getNextEntry()) != null) {
        String entryName = e.getName().toLowerCase(Locale.ROOT);
        if (entryName.contains("vba") || entryName.contains("/embeddings/"))
          throw fileError(415, "UNSUPPORTED_FILE_TYPE", "Không hỗ trợ macro/embedded object");
        if (++count > 2000
            || e.getName().startsWith("/")
            || e.getName().contains("..")
            || e.getName().contains("\\")
            || files.containsKey(e.getName()))
          throw fileError(422, "IMPORT_VALIDATION_FAILED", "ZIP entry không hợp lệ");
        var data = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = zip.read(buf)) != -1) {
          expanded += n;
          if (expanded > 20 * 1024 * 1024)
            throw fileError(413, "FILE_TOO_LARGE", "File giải nén quá lớn");
          data.write(buf, 0, n);
        }
        byte[] raw = data.toByteArray();
        if (e.getName().endsWith(".xml") || e.getName().endsWith(".rels")) {
          String xml = new String(raw, java.nio.charset.StandardCharsets.UTF_8);
          if (xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY"))
            throw fileError(422, "IMPORT_VALIDATION_FAILED", "Không hỗ trợ DTD/entity");
          if (e.getName().endsWith(".rels")) {
            var doc = document(raw);
            var rels = doc.getElementsByTagNameNS("*", "Relationship");
            for (int j = 0; j < rels.getLength(); j++)
              if ("External".equalsIgnoreCase(((Element) rels.item(j)).getAttribute("TargetMode")))
                throw fileError(
                    422, "IMPORT_VALIDATION_FAILED", "Không hỗ trợ external relationship");
          }
        }
        files.put(e.getName(), raw);
      }
      if (files.isEmpty() || !files.containsKey("[Content_Types].xml"))
        throw fileError(415, "UNSUPPORTED_FILE_TYPE", "Cần file OOXML không mã hóa");
      return files;
    } catch (ApiError e) {
      throw e;
    } catch (Exception e) {
      throw fileError(415, "UNSUPPORTED_FILE_TYPE", "File ZIP/OOXML không hợp lệ");
    }
  }

  static Document document(byte[] raw) throws Exception {
    var f = DocumentBuilderFactory.newInstance();
    f.setNamespaceAware(true);
    f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    f.setFeature("http://xml.org/sax/features/external-general-entities", false);
    f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
    f.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
    return f.newDocumentBuilder().parse(new ByteArrayInputStream(raw));
  }

  private List<Item> excel(byte[] bytes, List<Map<String, Object>> errors) {
    try (var book = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
      if (book.getNumberOfSheets() != 1
          || !book.getSheetName(0).equals("VOCABULARY")
          || book.isSheetHidden(0)
          || book.isSheetVeryHidden(0))
        throw fileError(422, "IMPORT_VALIDATION_FAILED", "Cần một sheet VOCABULARY hiện");
      var sheet = book.getSheetAt(0);
      if (sheet.getNumMergedRegions() > 0)
        throw fileError(422, "IMPORT_VALIDATION_FAILED", "Không hỗ trợ merged cell");
      String[] header = {"word", "meaning", "example", "phonetic"};
      var first = sheet.getRow(0);
      if (first == null || first.getLastCellNum() != 4)
        throw fileError(422, "IMPORT_VALIDATION_FAILED", "Header không hợp lệ");
      for (int i = 0; i < 4; i++)
        if (first.getCell(i) == null
            || first.getCell(i).getCellType() != CellType.STRING
            || !header[i].equals(first.getCell(i).getStringCellValue()))
          throw fileError(422, "IMPORT_VALIDATION_FAILED", "Header phải đúng thứ tự");
      var result = new ArrayList<Item>();
      for (var row : sheet) {
        if (row.getRowNum() == 0) continue;
        boolean nonempty = false;
        for (var c : row)
          if (c.getCellType() != CellType.BLANK
              && (c.getCellType() != CellType.STRING || !c.getStringCellValue().isBlank()))
            nonempty = true;
        if (!nonempty) continue;
        if (result.size() >= 1000)
          throw fileError(413, "FILE_TOO_LARGE", "Tối đa 1000 dòng có dữ liệu");
        var fields = new LinkedHashMap<String, Object>();
        for (int i = 0; i < 4; i++) {
          var cell = row.getCell(i);
          if (cell == null || cell.getCellType() == CellType.BLANK) fields.put(header[i], null);
          else if (cell.getCellType() != CellType.STRING) {
            fields.put(header[i], null);
            errors.add(
                issue(
                    row.getRowNum() + 1, null, header[i], "TEXT_REQUIRED", "Chỉ hỗ trợ cell text"));
          } else fields.put(header[i], cell.getStringCellValue());
        }
        for (var c : row)
          if (c.getColumnIndex() >= 4
              && c.getCellType() != CellType.BLANK
              && (c.getCellType() != CellType.STRING || !c.getStringCellValue().isBlank()))
            errors.add(
                issue(
                    row.getRowNum() + 1, null, "row", "EXTRA_COLUMN", "Có dữ liệu ngoài bốn cột"));
        result.add(new Item(row.getRowNum() + 1, fields));
      }
      return result;
    } catch (ApiError e) {
      throw e;
    } catch (Exception e) {
      throw fileError(422, "IMPORT_VALIDATION_FAILED", "Không thể đọc Excel");
    }
  }

  private List<Item> word(Map<String, byte[]> files, List<Map<String, Object>> errors) {
    try {
      if (!files.containsKey("word/document.xml"))
        throw fileError(415, "UNSUPPORTED_FILE_TYPE", "Không phải Word DOCX");
      var doc = document(files.get("word/document.xml"));
      for (String tag : List.of("tbl", "drawing", "object", "pict", "hyperlink", "altChunk"))
        if (doc.getElementsByTagNameNS(NS, tag).getLength() > 0)
          throw fileError(422, "IMPORT_VALIDATION_FAILED", "Word chỉ hỗ trợ paragraph text");
      var ps = doc.getElementsByTagNameNS(NS, "p");
      String title = null;
      int titleAt = 0;
      var body = new ArrayList<String>();
      for (int i = 0; i < ps.getLength(); i++) {
        var p = (Element) ps.item(i);
        StringBuilder text = new StringBuilder();
        appendText(p, text);
        String t = text.toString();
        if (title == null) {
          if (t.isBlank()) continue;
          title = t;
          titleAt = i + 1;
          var styles = p.getElementsByTagNameNS(NS, "pStyle");
          if (styles.getLength() == 0
              || !"Title".equals(((Element) styles.item(0)).getAttributeNS(NS, "val")))
            errors.add(
                issue(
                    null,
                    i + 1,
                    "title",
                    "TITLE_STYLE_REQUIRED",
                    "Paragraph không rỗng đầu tiên cần style Title"));
        } else body.add(t);
      }
      if (title == null) errors.add(issue(null, 1, "title", "REQUIRED", "Thiếu Title"));
      return List.of(
          new Item(
              titleAt == 0 ? 1 : titleAt,
              Json.nullable("title", title, "content", String.join("\n", body))));
    } catch (ApiError e) {
      throw e;
    } catch (Exception e) {
      throw fileError(422, "IMPORT_VALIDATION_FAILED", "Không thể đọc Word");
    }
  }

  static void appendText(Node n, StringBuilder text) {
    if (NS.equals(n.getNamespaceURI())) {
      if ("t".equals(n.getLocalName())) {
        text.append(n.getTextContent());
        return;
      }
      if ("br".equals(n.getLocalName())) text.append('\n');
      if ("tab".equals(n.getLocalName())) text.append('\t');
    }
    for (Node c = n.getFirstChild(); c != null; c = c.getNextSibling()) appendText(c, text);
  }

  public Map<String, Object> run(String type, MultipartFile file, UUID parent, Integer order) {
    String ext = type.equals("vocabulary") ? ".xlsx" : ".docx",
        mime =
            type.equals("vocabulary")
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    if (file.getOriginalFilename() == null
        || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(ext)
        || !mime.equals(file.getContentType()))
      throw fileError(415, "UNSUPPORTED_FILE_TYPE", "Sai định dạng import");
    if (file.getSize() > 5 * 1024 * 1024) throw fileError(413, "FILE_TOO_LARGE", "Tối đa 5 MiB");
    store.row(type.equals("vocabulary") ? "lessons" : "modules", parent);
    if (!type.equals("vocabulary") && (order == null || order < 1))
      throw ApiError.bad("orderNo", "Cần số nguyên dương");
    byte[] bytes;
    try {
      bytes = file.getBytes();
    } catch (IOException e) {
      throw fileError(422, "IMPORT_VALIDATION_FAILED", "Không thể đọc file");
    }
    var files = inspect(bytes);
    if (type.equals("vocabulary") && !files.containsKey("xl/workbook.xml"))
      throw fileError(415, "UNSUPPORTED_FILE_TYPE", "Không phải Excel XLSX");
    var errors = new ArrayList<Map<String, Object>>();
    var items = type.equals("vocabulary") ? excel(bytes, errors) : word(files, errors);
    Set<String> keys = new HashSet<>();
    boolean dbDuplicate = false;
    for (var item : items) {
      var f = item.fields;
      for (var field :
          type.equals("vocabulary")
              ? List.of("word", "meaning", "example", "phonetic")
              : List.of("title", "content")) {
        int max =
            switch (field) {
              case "word", "phonetic" -> 128;
              case "meaning" -> 500;
              case "example" -> 1000;
              case "title" -> 200;
              default -> 20000;
            };
        try {
          f.put(
              field, Input.text(f.get(field), max, Set.of("example", "phonetic").contains(field)));
        } catch (ApiError e) {
          errors.add(
              issue(
                  type.equals("vocabulary") ? item.location : null,
                  type.equals("vocabulary") ? null : item.location,
                  field,
                  "INVALID",
                  e.getMessage()));
        }
      }
      if (type.equals("vocabulary") && f.get("word") != null && f.get("meaning") != null) {
        String w = Input.norm(f.get("word").toString()),
            m = Input.norm(f.get("meaning").toString()),
            key = w + "\u0000" + m;
        if (!keys.add(key))
          errors.add(
              issue(item.location, null, "word", "IMPORT_DUPLICATE", "Trùng từ/nghĩa trong file"));
        if (db.count(
                "SELECT count(*) FROM vocabulary WHERE lesson_id=? AND word_normalized=? AND"
                    + " meaning_normalized=?",
                parent,
                w,
                m)
            > 0) {
          dbDuplicate = true;
          errors.add(
              issue(item.location, null, "word", "IMPORT_DUPLICATE", "Từ đã có trong Lesson"));
        }
      }
      if (type.equals("lessons")
          && f.get("title") != null
          && db.count(
                  "SELECT count(*) FROM lesson WHERE module_id=? AND (title_normalized=? OR"
                      + " order_no=?)",
                  parent,
                  Input.norm(f.get("title").toString()),
                  order)
              > 0) {
        dbDuplicate = true;
        errors.add(
            issue(
                null,
                item.location,
                "title",
                "IMPORT_DUPLICATE",
                "Trùng title/thứ tự trong Module"));
      }
    }
    if (items.isEmpty())
      errors.add(issue(null, null, "file", "EMPTY_IMPORT", "File không có nội dung"));
    if (!errors.isEmpty()) {
      long invalid =
          errors.stream()
              .map(x -> x.get(type.equals("vocabulary") ? "row" : "paragraph"))
              .filter(Objects::nonNull)
              .distinct()
              .count();
      int inv = (int) Math.min(invalid, items.size());
      throw new ApiError(
          dbDuplicate ? 409 : 422,
          dbDuplicate ? "IMPORT_DUPLICATE" : "IMPORT_VALIDATION_FAILED",
          "Import bị từ chối",
          List.of(),
          Map.of(
              "import",
              outcome("REJECTED", items.size(), items.size() - inv, inv, 0, List.of(), errors)));
    }
    try {
      return db.tx(
          () -> {
            var ids = new ArrayList<UUID>();
            for (var item : items) {
              var f = item.fields;
              UUID id = UUID.randomUUID();
              if (type.equals("vocabulary"))
                db.run(
                    "INSERT INTO"
                        + " vocabulary(vocabulary_id,lesson_id,word,meaning,example,phonetic,word_normalized,meaning_normalized)"
                        + " VALUES(?,?,?,?,?,?,?,?)",
                    id,
                    parent,
                    f.get("word"),
                    f.get("meaning"),
                    f.get("example"),
                    f.get("phonetic"),
                    Input.norm(f.get("word").toString()),
                    Input.norm(f.get("meaning").toString()));
              else
                db.run(
                    "INSERT INTO"
                        + " lesson(lesson_id,module_id,title,title_normalized,content,order_no)"
                        + " VALUES(?,?,?,?,?,?)",
                    id,
                    parent,
                    f.get("title"),
                    Input.norm(f.get("title").toString()),
                    f.get("content"),
                    order);
              ids.add(id);
            }
            return outcome("SUCCESS", items.size(), items.size(), 0, ids.size(), ids, List.of());
          });
    } catch (ApiError e) {
      throw new ApiError(
          e.status,
          e.status == 409 ? "IMPORT_DUPLICATE" : e.code,
          e.getMessage(),
          List.of(),
          Map.of(
              "import",
              outcome(
                  "REJECTED",
                  items.size(),
                  items.size(),
                  0,
                  0,
                  List.of(),
                  List.of(issue(null, null, "file", e.code, "Transaction đã rollback")))));
    }
  }
}
