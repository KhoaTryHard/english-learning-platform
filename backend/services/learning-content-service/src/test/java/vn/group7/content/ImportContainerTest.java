package vn.group7.content;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Supplier;
import java.util.zip.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockMultipartFile;
import vn.group7.core.*;

class ImportContainerTest {
  static byte[] fixture(String name, boolean macro) throws Exception {
    var bytes = new ByteArrayOutputStream();
    try (var stream = ImportContainerTest.class.getResourceAsStream("/fixtures/" + name);
        var input = new ZipInputStream(Objects.requireNonNull(stream));
        var output = new ZipOutputStream(bytes)) {
      ZipEntry entry;
      boolean changed = false;
      while ((entry = input.getNextEntry()) != null) {
        byte[] data = input.readAllBytes();
        if (macro && entry.getName().equals("[Content_Types].xml")) {
          String xml = new String(data, StandardCharsets.UTF_8);
          String replacement = xml
              .replace("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml",
                  "application/vnd.ms-excel.sheet.macroEnabled.main+xml")
              .replace("application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml",
                  "application/vnd.ms-word.document.macroEnabled.main+xml");
          changed = !replacement.equals(xml);
          data = replacement.getBytes(StandardCharsets.UTF_8);
        }
        output.putNextEntry(new ZipEntry(entry.getName()));
        output.write(data);
        output.closeEntry();
      }
      assertTrue(!macro || changed, "Fixture must actually contain a macro-enabled content type");
    }
    return bytes.toByteArray();
  }

  static MockMultipartFile upload(String name, boolean macro) throws Exception {
    String mime = name.endsWith("xlsx")
        ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    return new MockMultipartFile("file", name, mime, fixture(name, macro));
  }

  @ParameterizedTest
  @CsvSource({"vocabulary,vocabulary-valid.xlsx", "lessons,lesson-valid.docx"})
  void renamedMacroContainerIsRejectedBeforeTransaction(String type, String name) throws Exception {
    Db db = mock(Db.class);
    var importer = new Imports(db, mock(ContentStore.class));
    var file = upload(name, true);
    var error = assertThrows(ApiError.class,
        () -> importer.run(type, file, UUID.randomUUID(), 1));
    assertEquals(415, error.status);
    assertEquals("UNSUPPORTED_FILE_TYPE", error.code);
    var outcome = (Map<?, ?>) error.extra.get("import");
    assertEquals("REJECTED", outcome.get("status"));
    assertEquals(0, outcome.get("createdCount"));
    assertEquals(List.of(), outcome.get("createdIds"));
    verifyNoInteractions(db);
  }

  @ParameterizedTest
  @CsvSource({"vocabulary,vocabulary-valid.xlsx,2", "lessons,lesson-valid.docx,1"})
  void ordinaryContainerStillImports(String type, String name, int count) throws Exception {
    Db db = mock(Db.class);
    when(db.tx(any())).thenAnswer(call -> ((Supplier<?>) call.getArgument(0)).get());
    var importer = new Imports(db, mock(ContentStore.class));
    var result = importer.run(type, upload(name, false), UUID.randomUUID(), 1);
    assertEquals("SUCCESS", result.get("status"));
    assertEquals(count, result.get("createdCount"));
    verify(db).tx(any());
  }
}
