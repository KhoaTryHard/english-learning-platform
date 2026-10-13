package vn.group7.content;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import vn.group7.core.*;

class ImportBoundaryTest {
  byte[] zip(String name, byte[] content) throws Exception {
    var bytes = new ByteArrayOutputStream();
    try (var zip = new ZipOutputStream(bytes)) {
      zip.putNextEntry(new ZipEntry(name));
      zip.write(content);
      zip.closeEntry();
      zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
      zip.write("<Types/>".getBytes());
      zip.closeEntry();
    }
    return bytes.toByteArray();
  }

  @Test
  void zipTraversalAndEntityAreRejected() throws Exception {
    byte[] traversal = zip("../escape.xml", "x".getBytes());
    assertEquals(422, assertThrows(ApiError.class, () -> Imports.inspect(traversal)).status);
    byte[] entity =
        zip(
            "word/document.xml",
            "<!DOCTYPE x [<!ENTITY y SYSTEM 'file:///etc/passwd'>]><x/>".getBytes());
    assertEquals(422, assertThrows(ApiError.class, () -> Imports.inspect(entity)).status);
  }

  @Test
  void expandedSizeIsBounded() throws Exception {
    byte[] bomb = zip("word/document.xml", new byte[21 * 1024 * 1024]);
    assertEquals(413, assertThrows(ApiError.class, () -> Imports.inspect(bomb)).status);
  }

  @Test
  void unavailableFallbackDoesNotWeakenForeignKeyOrMaskDatabaseOutage() {
    Db db = mock(Db.class);
    var relation =
        Json.nullable(
            "vocabulary_id",
            UUID.randomUUID(),
            "added_at",
            java.sql.Timestamp.from(java.time.Instant.now()));
    when(db.rows(anyString(), any())).thenReturn(List.of());
    var store = new ContentStore(db, mock(OwnerClient.class), mock(Media.class), "http://unused");
    var result = store.saved(relation);
    assertEquals("UNAVAILABLE", result.get("availability"));
    assertNull(result.get("content"));
    when(db.rows(anyString(), any())).thenThrow(ApiError.dependency());
    assertEquals(503, assertThrows(ApiError.class, () -> store.saved(relation)).status);
  }
}
