package vn.group7.content;

import java.io.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.group7.core.*;

@RestController
public class ImportController {
  private final Imports imports;

  public ImportController(Imports i) {
    imports = i;
  }

  @PostMapping(value = "/api/v1/admin/imports/vocabulary", consumes = "multipart/form-data")
  ResponseEntity<?> vocabulary(@RequestParam MultipartFile file, @RequestParam String lessonId) {
    return ResponseEntity.status(201)
        .body(imports.run("vocabulary", file, Input.id(lessonId), null));
  }

  @PostMapping(value = "/api/v1/admin/imports/lessons", consumes = "multipart/form-data")
  ResponseEntity<?> lessons(
      @RequestParam MultipartFile file,
      @RequestParam String moduleId,
      @RequestParam Integer orderNo) {
    return ResponseEntity.status(201)
        .body(imports.run("lessons", file, Input.id(moduleId), orderNo));
  }

  @GetMapping("/api/v1/admin/import-templates/{type}")
  ResponseEntity<?> template(@PathVariable String type) throws IOException {
    if (!Set.of("vocabulary", "lessons").contains(type)) throw ApiError.missing();
    String ext = type.equals("vocabulary") ? "xlsx" : "docx",
        mime =
            type.equals("vocabulary")
                ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    try (var stream = getClass().getResourceAsStream("/templates/" + type + "." + ext)) {
      return ResponseEntity.ok()
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + type + "." + ext)
          .contentType(MediaType.parseMediaType(mime))
          .body(stream.readAllBytes());
    }
  }
}
