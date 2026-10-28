package vn.group7.content;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import vn.group7.core.*;

@RestController
public class ContentController {
  private final ContentStore store;
  private final Db db;

  public ContentController(ContentStore s, Db d) {
    store = s;
    db = d;
  }

  String resource(HttpServletRequest r) {
    return r.getRequestURI().split("/")[4];
  }

  @GetMapping({
    "/api/v1/admin/courses",
    "/api/v1/admin/modules",
    "/api/v1/admin/lessons",
    "/api/v1/admin/vocabulary",
    "/api/v1/admin/learning-contents"
  })
  Map<String, Object> adminList(
      HttpServletRequest r,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String courseId,
      @RequestParam(required = false) String moduleId,
      @RequestParam(required = false) String lessonId,
      @RequestParam(required = false) String type) {
    String res = resource(r), parent = null, id = null;
    if (res.equals("modules")) {
      parent = "course_id";
      id = courseId;
    }
    if (res.equals("lessons")) {
      parent = "module_id";
      id = moduleId;
    }
    if (Set.of("vocabulary", "learning-contents").contains(res)) {
      parent = "lesson_id";
      id = lessonId;
    }
    return store.list(
        res,
        parent,
        id == null ? null : Input.id(id),
        res.equals("learning-contents") ? type : null,
        Input.page(page),
        Input.size(size),
        false);
  }

  @GetMapping({
    "/api/v1/admin/courses/{id}",
    "/api/v1/admin/modules/{id}",
    "/api/v1/admin/lessons/{id}",
    "/api/v1/admin/vocabulary/{id}",
    "/api/v1/admin/learning-contents/{id}"
  })
  Map<String, Object> adminGet(HttpServletRequest r, @PathVariable String id) {
    return store.get(resource(r), Input.id(id), false);
  }

  @PostMapping({
    "/api/v1/admin/courses",
    "/api/v1/admin/modules",
    "/api/v1/admin/lessons",
    "/api/v1/admin/learning-contents"
  })
  ResponseEntity<?> create(HttpServletRequest r, @RequestBody Map<String, Object> b) {
    return ResponseEntity.status(201).body(store.create(resource(r), b));
  }

  @PatchMapping({
    "/api/v1/admin/courses/{id}",
    "/api/v1/admin/modules/{id}",
    "/api/v1/admin/lessons/{id}",
    "/api/v1/admin/vocabulary/{id}",
    "/api/v1/admin/learning-contents/{id}"
  })
  Map<String, Object> patch(
      HttpServletRequest r, @PathVariable String id, @RequestBody Map<String, Object> b) {
    return store.patch(resource(r), Input.id(id), b);
  }

  @GetMapping("/api/v1/courses")
  Map<String, Object> courses(
      @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
    return store.list("courses", null, null, null, Input.page(page), Input.size(size), true);
  }

  @GetMapping("/api/v1/courses/{id}")
  Map<String, Object> course(@PathVariable String id) {
    return store.get("courses", Input.id(id), true);
  }

  @GetMapping("/api/v1/courses/{id}/levels")
  Map<String, Object> levels(
      @PathVariable String id,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    UUID uid = Input.id(id);
    store.get("courses", uid, true);
    int p = Input.page(page), s = Input.size(size);
    var levels = store.levels(uid);
    int start = (int) Math.min((long) p * s, levels.size());
    return Map.of(
        "items",
        levels.subList(start, Math.min(start + s, levels.size())),
        "page",
        p,
        "size",
        s,
        "totalElements",
        levels.size(),
        "totalPages",
        (levels.size() + s - 1) / s);
  }

  @GetMapping("/api/v1/courses/{id}/modules")
  Map<String, Object> modules(
      @PathVariable String id,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    UUID uid = Input.id(id);
    store.get("courses", uid, true);
    return store.list("modules", "course_id", uid, null, Input.page(page), Input.size(size), true);
  }

  @GetMapping("/api/v1/modules/{id}/lessons")
  Map<String, Object> lessons(
      @PathVariable String id,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    UUID uid = Input.id(id);
    store.get("modules", uid, true);
    return store.list("lessons", "module_id", uid, null, Input.page(page), Input.size(size), true);
  }

  @GetMapping("/api/v1/lessons/{id}")
  Map<String, Object> lesson(@PathVariable String id) {
    return store.learning(Input.id(id));
  }

  @GetMapping("/api/v1/me/vocabulary")
  Map<String, Object> saved(
      HttpServletRequest r,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return store.savedList(Principal.get(r).userId(), Input.page(page), Input.size(size));
  }

  @PutMapping("/api/v1/me/vocabulary/{id}")
  Map<String, Object> save(
      HttpServletRequest r, @PathVariable String id, @RequestBody(required = false) Object body) {
    if (body != null) throw ApiError.bad("body", "Không có request body");
    return store.save(Principal.get(r).userId(), Input.id(id));
  }

  @DeleteMapping("/api/v1/me/vocabulary/{id}")
  ResponseEntity<?> remove(HttpServletRequest r, @PathVariable String id) {
    store.remove(Principal.get(r).userId(), Input.id(id));
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/internal/v1/courses/{id}/context")
  Map<String, Object> context(@PathVariable String id) {
    return store.courseContext(Input.id(id));
  }

  @GetMapping("/internal/v1/courses/contexts")
  Map<String, Object> contexts(
      @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer size) {
    int p = Input.page(page), s = Input.size(size == null ? 100 : size);
    long n = db.count("SELECT count(*) FROM course");
    return Map.of(
        "items",
        db
            .rows(
                "SELECT course_id FROM course ORDER BY course_id LIMIT ? OFFSET ?", s, (long) p * s)
            .stream()
            .map(x -> store.courseContext((UUID) x.get("course_id")))
            .toList(),
        "page",
        p,
        "size",
        s,
        "totalElements",
        n,
        "generatedAt",
        java.time.Instant.now().toString());
  }

  @GetMapping("/internal/v1/lessons/{id}/context")
  Map<String, Object> lessonContext(@PathVariable String id) {
    return store.lessonContext(Input.id(id));
  }
}
