package vn.group7.progress;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import vn.group7.core.*;

@RestController
@RequestMapping("/api/v1/me")
public class ProgressController {
  private final ProgressService service;

  public ProgressController(ProgressService service) {
    this.service = service;
  }

  @GetMapping("/progress")
  public Map<String, Object> progress(@RequestParam String courseId, HttpServletRequest r) {
    return service.progress(Principal.get(r).userId(), Input.id(courseId));
  }

  @GetMapping("/history")
  public Map<String, Object> history(
      @RequestParam(required = false) String courseId,
      @RequestParam(required = false) String type,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      HttpServletRequest r) {
    return service.history(
        Principal.get(r).userId(), courseId == null ? null : Input.id(courseId), type, page, size);
  }

  @GetMapping("/dashboard")
  public Map<String, Object> dashboard(HttpServletRequest r) {
    return service.dashboard(Principal.get(r).userId());
  }
}
