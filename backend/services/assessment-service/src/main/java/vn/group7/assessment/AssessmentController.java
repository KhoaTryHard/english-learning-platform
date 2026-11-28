package vn.group7.assessment;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.group7.core.*;

@RestController
public class AssessmentController {
  private final AssessmentService service;

  public AssessmentController(AssessmentService service) {
    this.service = service;
  }

  private static String id(String s) {
    return Input.id(s).toString();
  }

  private static String optionalId(String s) {
    return s == null ? null : id(s);
  }

  private static String user(HttpServletRequest r) {
    return Principal.get(r).userId().toString();
  }

  private static void noBody(String body) {
    if (body != null && !body.isBlank()) throw ApiError.bad("body", "Request body is not allowed");
  }

  @PostMapping("/api/v1/admin/assessments")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@RequestBody Map<String, Object> b) {
    return service.create(b);
  }

  @PatchMapping("/api/v1/admin/assessments/{assessmentId}")
  public Map<String, Object> patch(
      @PathVariable String assessmentId, @RequestBody Map<String, Object> b) {
    return service.patch(id(assessmentId), b);
  }

  @GetMapping("/api/v1/admin/assessments/{assessmentId}")
  public Map<String, Object> adminGet(@PathVariable String assessmentId) {
    return service.get(id(assessmentId), true);
  }

  @GetMapping("/api/v1/assessments/{assessmentId}")
  public Map<String, Object> get(@PathVariable String assessmentId) {
    return service.get(id(assessmentId), false);
  }

  @GetMapping("/api/v1/admin/assessments")
  public Map<String, Object> adminList(
      @RequestParam(required = false) String courseId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return service.list(
        true, optionalId(courseId), type, Validation.page(page), Validation.size(size));
  }

  @GetMapping("/api/v1/assessments")
  public Map<String, Object> list(
      @RequestParam(required = false) String courseId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    return service.list(
        false, optionalId(courseId), type, Validation.page(page), Validation.size(size));
  }

  @PostMapping("/api/v1/admin/assessments/{assessmentId}/questions")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createQuestion(
      @PathVariable String assessmentId, @RequestBody Map<String, Object> b) {
    return service.putQuestion(id(assessmentId), null, b);
  }

  @PutMapping("/api/v1/admin/assessments/{assessmentId}/questions/{questionId}")
  public Map<String, Object> replaceQuestion(
      @PathVariable String assessmentId,
      @PathVariable String questionId,
      @RequestBody Map<String, Object> b) {
    return service.putQuestion(id(assessmentId), id(questionId), b);
  }

  @GetMapping("/api/v1/lessons/{lessonId}/exercise")
  public Map<String, Object> exercise(@PathVariable String lessonId) {
    return service.lessonExercise(id(lessonId));
  }

  @GetMapping("/internal/v1/lessons/{lessonId}/exercise-readiness")
  public Map<String, Object> readiness(@PathVariable String lessonId) {
    return service.readiness(id(lessonId));
  }

  @PostMapping("/api/v1/assessments/{assessmentId}/attempts")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> start(
      @PathVariable String assessmentId,
      @RequestHeader("Idempotency-Key") String key,
      @RequestBody(required = false) String body,
      HttpServletRequest r) {
    noBody(body);
    return service.start(id(assessmentId), user(r), id(key));
  }

  @GetMapping("/api/v1/attempts/{attemptId}")
  public Map<String, Object> attempt(@PathVariable String attemptId, HttpServletRequest r) {
    return service.attempt(id(attemptId), user(r));
  }

  @PutMapping("/api/v1/attempts/{attemptId}/answers")
  public Map<String, Object> save(
      @PathVariable String attemptId, @RequestBody Map<String, Object> b, HttpServletRequest r) {
    return service.save(id(attemptId), user(r), b);
  }

  @PostMapping("/api/v1/attempts/{attemptId}/submit")
  public Map<String, Object> submit(
      @PathVariable String attemptId,
      @RequestBody(required = false) String body,
      HttpServletRequest r) {
    noBody(body);
    return service.submit(id(attemptId), user(r), r.getAttribute("traceId").toString());
  }

  @GetMapping("/api/v1/results/{resultId}")
  public Map<String, Object> result(@PathVariable String resultId, HttpServletRequest r) {
    return service.result(id(resultId), user(r));
  }

  @GetMapping("/api/v1/me/attempts")
  public Map<String, Object> attempts(
      @RequestParam(required = false) String assessmentId,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      HttpServletRequest r) {
    return service.attempts(
        user(r), optionalId(assessmentId), status, Validation.page(page), Validation.size(size));
  }

  @GetMapping("/api/v1/me/results")
  public Map<String, Object> results(
      @RequestParam(required = false) String courseId,
      @RequestParam(required = false) String assessmentId,
      @RequestParam(required = false) String type,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      HttpServletRequest r) {
    return service.results(
        user(r),
        optionalId(courseId),
        optionalId(assessmentId),
        type,
        Validation.page(page),
        Validation.size(size));
  }
}
