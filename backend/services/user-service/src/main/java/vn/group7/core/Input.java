package vn.group7.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public final class Input {
  private static final JsonNode S;

  static {
    try {
      S = Json.M.readTree(Input.class.getResourceAsStream("/public-schemas.json"));
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public static Map<String, Object> check(String schema, Map<String, Object> b) {
    if (b == null) throw ApiError.bad("body", "Thiếu dữ liệu");
    validate(S.get(schema), b, "body");
    return b;
  }

  private static void validate(JsonNode s, Object x, String field) {
    if (s.has("$ref")) {
      validate(
          S.get(s.get("$ref").asText().substring(s.get("$ref").asText().lastIndexOf('/') + 1)),
          x,
          field);
      return;
    }
    if (x == null) {
      if (!s.path("nullable").asBoolean()) throw ApiError.bad(field, "Không được null");
      return;
    }
    String type = s.path("type").asText();
    if (type.equals("object")) {
      if (!(x instanceof Map<?, ?>)) throw ApiError.bad(field, "Phải là object");
      var m = (Map<?, ?>) x;
      if (m.size() < s.path("minProperties").asInt())
        throw ApiError.bad(field, "Cần trường cập nhật");
      for (var r : s.path("required"))
        if (!m.containsKey(r.asText())) throw ApiError.bad(r.asText(), "Thiếu trường bắt buộc");
      for (var k : m.keySet()) {
        var p = s.path("properties").get(k.toString());
        if (p == null) throw ApiError.bad(k.toString(), "Trường không được phép");
        validate(p, m.get(k), k.toString());
      }
    }
    if (type.equals("string")) {
      if (!(x instanceof String)) throw ApiError.bad(field, "Phải là text");
      String v = (String) x;
      int n = v.codePointCount(0, v.length());
      if (n < s.path("minLength").asInt() || n > s.path("maxLength").asInt(Integer.MAX_VALUE))
        throw ApiError.bad(field, "Độ dài không hợp lệ");
      String f = s.path("format").asText();
      if (f.equals("uuid")) id(v);
      if (f.equals("email") && !v.trim().matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))
        throw ApiError.bad(field, "Email không hợp lệ");
    }
    if (type.equals("integer")
        && (!(x instanceof Number) || ((Number) x).doubleValue() != ((Number) x).intValue()))
      throw ApiError.bad(field, "Phải là số nguyên");
    if (type.equals("integer") || type.equals("number")) {
      if (!(x instanceof Number)) throw ApiError.bad(field, "Phải là số");
      double n = ((Number) x).doubleValue();
      if (!Double.isFinite(n)
          || n < s.path("minimum").asDouble(-Double.MAX_VALUE)
          || n > s.path("maximum").asDouble(Double.MAX_VALUE))
        throw ApiError.bad(field, "Số ngoài giới hạn");
    }
    if (type.equals("boolean") && !(x instanceof Boolean))
      throw ApiError.bad(field, "Phải là boolean");
    if (s.has("enum")) {
      boolean ok = false;
      for (var v : s.get("enum")) if (v.asText().equals(x.toString())) ok = true;
      if (!ok) throw ApiError.bad(field, "Giá trị không hỗ trợ");
    }
  }

  public static UUID id(String s) {
    try {
      UUID u = UUID.fromString(s);
      if (!u.toString().equalsIgnoreCase(s)) throw new IllegalArgumentException();
      return u;
    } catch (Exception e) {
      throw ApiError.bad("id", "UUID không hợp lệ");
    }
  }

  public static String text(Object v, int max, boolean optional) {
    if (v == null && optional) return null;
    if (!(v instanceof String)) throw ApiError.bad("text", "Thiếu text");
    String s = java.text.Normalizer.normalize(((String) v).strip(), java.text.Normalizer.Form.NFC);
    if (s.isBlank()) {
      if (optional) return null;
      throw ApiError.bad("text", "Không được rỗng");
    }
    if (s.codePointCount(0, s.length()) > max) throw ApiError.bad("text", "Text quá dài");
    return s;
  }

  public static String norm(String s) {
    return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFC)
        .strip()
        .replaceAll("(?U)\\s+", " ")
        .toLowerCase(Locale.ROOT);
  }

  public static int page(Integer p) {
    if (p == null) return 0;
    if (p < 0 || p > 10000000) throw ApiError.bad("page", "Page không hợp lệ");
    return p;
  }

  public static int size(Integer s) {
    if (s == null) return 20;
    if (s < 1 || s > 100) throw ApiError.bad("size", "Size không hợp lệ");
    return s;
  }
}
