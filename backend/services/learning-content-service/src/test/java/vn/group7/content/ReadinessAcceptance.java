package vn.group7.content;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import vn.group7.core.*;

/** Opt-in acceptance driver. Uses the existing TokenCodec; never writes credentials/tokens. */
final class ReadinessAcceptance {
  static final List<String> CASES =
      List.of("missingMapping", "notReady", "ready", "serviceDown", "databaseDown", "recovery");
  final Map<String, Object> manifest;
  final List<Map<String, Object>> checks = new ArrayList<>();
  final HttpClient http;
  final String origin;
  final Path keys;
  final Path privateKeys;
  String csrf;

  ReadinessAcceptance(Map<String, Object> manifest, Path keys, Path privateKeys, String origin) {
    this.manifest = manifest;
    this.keys = keys;
    this.privateKeys = privateKeys;
    this.origin = origin;
    http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
        .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
  }

  static void require(boolean condition, String message) {
    if (!condition) throw new IllegalStateException(message);
  }

  static Map<String, Object> object(Object value) {
    require(value instanceof Map<?, ?>, "Expected JSON object");
    @SuppressWarnings("unchecked") var result = (Map<String, Object>) value;
    return result;
  }

  static String text(Map<String, Object> source, String key) {
    Object value = source.get(key);
    require(value instanceof String s && !s.isBlank(), "Missing manifest field: " + key);
    return (String) value;
  }

  static String uuid(Map<String, Object> source, String key) {
    String value = text(source, key);
    require(UUID.fromString(value).toString().equals(value), "Invalid UUID field: " + key);
    return value;
  }

  static void validate(Map<String, Object> manifest, boolean prepared) {
    require("TEST".equals(manifest.get("environment")), "Only explicit TEST environments are allowed");
    for (String key : List.of("gatewayUrl", "assessmentUrl", "assessmentUrlFromContent")) {
      URI uri = URI.create(text(manifest, key));
      require(Set.of("http", "https").contains(uri.getScheme()) && uri.getHost() != null
          && uri.getUserInfo() == null && uri.getQuery() == null && uri.getFragment() == null
          && (uri.getPath().isEmpty() || uri.getPath().equals("/")), "Invalid service base URL: " + key);
    }
    uuid(manifest, "courseId"); uuid(manifest, "levelId");
    require(text(manifest, "backendSha").matches("[0-9a-f]{40}"), "Record full backend SHA");
    if (!prepared) return;
    var assessment = object(manifest.get("assessment"));
    require(text(assessment, "sourceSha").matches("[0-9a-f]{40}"), "Huy must supply full Assessment SHA");
    for (String key : List.of("image", "dbEngine", "migration", "seedCommand", "runtimeEvidence"))
      text(assessment, key);
    require("LIVE".equals(assessment.get("kind")), "Assessment must be declared LIVE; mocks are not acceptance");
    require(!text(assessment, "image").toLowerCase(Locale.ROOT).contains("wiremock"), "WireMock is not LIVE");
    var fixtures = object(manifest.get("fixtures"));
    var lessons = new HashSet<String>();
    for (String name : CASES) {
      var fixture = object(fixtures.get(name));
      require(lessons.add(uuid(fixture, "lessonId")), "Each case requires a separate draft Lesson");
      if (!Set.of("missingMapping", "notReady").contains(name)) {
        uuid(fixture, "exerciseId");
        require(fixture.get("questionCount") instanceof Number n && n.intValue() >= 1,
            "Supply expected positive questionCount: " + name);
      }
    }
  }

  Map<String, Object> fixture(String name) {
    return object(object(manifest.get("fixtures")).get(name));
  }

  String readinessPath(String name) {
    return "/internal/v1/lessons/" + uuid(fixture(name), "lessonId") + "/exercise-readiness";
  }

  TokenCodec caller(String self) {
    return new TokenCodec(keys.toString(), self, privateKeys.resolve(self + ".private.pem").toString());
  }

  Map<String, Object> request(String base, String method, String path, Object body,
      String bearer, int expected, String check) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create(base.replaceAll("/+$", "") + path))
        .timeout(Duration.ofSeconds(40)).header("Content-Type", "application/json")
        .header("Origin", origin).method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(Json.text(body)));
    if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
    if (csrf != null && base.equals(manifest.get("gatewayUrl"))) builder.header("X-CSRF-Token", csrf);
    var response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    checks.add(Map.of("case", check, "method", method, "path", path, "expected", expected,
        "actual", response.statusCode()));
    // Do not include response bodies: login/CSRF and dependency errors may contain sensitive data.
    require(response.statusCode() == expected, check + ": expected " + expected + ", got " + response.statusCode());
    return response.body().isBlank() ? Map.of() : Json.map(response.body());
  }

  Map<String, Object> gateway(String method, String path, Object body, int expected, String check)
      throws Exception {
    return request(text(manifest, "gatewayUrl"), method, path, body, null, expected, check);
  }

  void csrf() throws Exception {
    csrf = text(gateway("GET", "/api/v1/auth/csrf", null, 200, "csrf"), "csrfToken");
  }

  void login(String email, String password) throws Exception {
    csrf();
    gateway("POST", "/api/v1/auth/login", Map.of("email", email, "password", password), 200, "login");
    csrf();
  }

  void prepare() throws Exception {
    require(!manifest.containsKey("fixtures"), "Prepare requires a fresh manifest without fixtures");
    gateway("PATCH", "/api/v1/admin/courses/" + uuid(manifest, "courseId"), Map.of("published", true), 200, "publish course");
    String suffix = UUID.randomUUID().toString();
    var module = gateway("POST", "/api/v1/admin/modules", Map.of("courseId", uuid(manifest, "courseId"),
        "title", "Readiness acceptance " + suffix, "orderNo", (int) Instant.now().getEpochSecond()), 201, "create module");
    String mid = uuid(module, "moduleId");
    gateway("PATCH", "/api/v1/admin/modules/" + mid, Map.of("published", true), 200, "publish module");
    var fixtures = new LinkedHashMap<String, Object>();
    int order = 0;
    for (String name : CASES) {
      var lesson = gateway("POST", "/api/v1/admin/lessons", Map.of("moduleId", mid,
          "title", "Readiness " + name, "content", "Acceptance test fixture", "orderNo", ++order), 201, "create " + name);
      fixtures.put(name, Json.nullable("lessonId", uuid(lesson, "lessonId"), "exerciseId", null, "questionCount", null));
    }
    manifest.put("moduleId", mid);
    manifest.put("fixtures", fixtures);
  }

  Map<String, Object> direct(String name, int expected) throws Exception {
    return request(text(manifest, "assessmentUrl"), "GET", readinessPath(name), null,
        caller("content").service("assessment"), expected, "direct " + name);
  }

  void readyResponse(String name) throws Exception {
    var result = direct(name, 200);
    var expected = fixture(name);
    require(result.keySet().equals(Set.of("lessonId", "ready", "exerciseId", "questionCount"))
        && expected.get("lessonId").equals(result.get("lessonId")) && Boolean.TRUE.equals(result.get("ready"))
        && expected.get("exerciseId").equals(result.get("exerciseId"))
        && result.get("questionCount") instanceof Number n
        && n.intValue() == ((Number) expected.get("questionCount")).intValue(), "Readiness values differ: " + name);
  }

  void draft(String name) throws Exception {
    var lesson = gateway("GET", "/api/v1/admin/lessons/" + uuid(fixture(name), "lessonId"), null, 200, "draft " + name);
    require(Boolean.FALSE.equals(lesson.get("published")), "Fixture must remain draft: " + name);
  }

  void publish(String name, int expected, String code) throws Exception {
    draft(name);
    var response = gateway("PATCH", "/api/v1/admin/lessons/" + uuid(fixture(name), "lessonId"),
        Map.of("published", true), expected, "publish " + name);
    if (code != null) { require(code.equals(response.get("code")), "Unexpected publish error code"); draft(name); }
    else require(Boolean.TRUE.equals(response.get("published")), "Published flag did not persist");
  }

  void online() throws Exception {
    for (String name : List.of("missingMapping", "notReady")) {
      var result = direct(name, 200);
      require(result.keySet().equals(Set.of("lessonId", "ready", "exerciseId", "questionCount"))
          && fixture(name).get("lessonId").equals(result.get("lessonId"))
          && Boolean.FALSE.equals(result.get("ready"))
          && result.get("questionCount") instanceof Number n && n.intValue() >= 0,
          "Expected not-ready source response: " + name);
      if (name.equals("missingMapping")) require(result.get("exerciseId") == null, "Missing mapping must not invent exerciseId");
      publish(name, 409, "ASSESSMENT_NOT_READY");
    }
    readyResponse("ready");
    for (String name : List.of("serviceDown", "databaseDown", "recovery")) { draft(name); readyResponse(name); }
    request(text(manifest, "assessmentUrl"), "GET", readinessPath("ready"), null,
        caller("api").service("assessment"), 403, "wrong caller");
    String valid = caller("content").service("assessment");
    String[] parts = valid.split("\\.");
    char first = parts[2].charAt(0);
    parts[2] = (first == 'A' ? "B" : "A") + parts[2].substring(1);
    request(text(manifest, "assessmentUrl"), "GET", readinessPath("ready"), null,
        String.join(".", parts), 401, "invalid signature");
    publish("ready", 200, null);
    learnerReads("ready");
  }

  void outage(boolean database) throws Exception {
    String name = database ? "databaseDown" : "serviceDown";
    if (database) {
      var error = direct(name, 503);
      require("DEPENDENCY_UNAVAILABLE".equals(error.get("code")), "DB outage must return dependency error");
    } else {
      try {
        // A stopped process must not serve readiness, including an HTTP 503 page from a proxy.
        http.send(HttpRequest.newBuilder(URI.create(text(manifest, "assessmentUrl") + readinessPath(name)))
            .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.discarding());
        throw new IllegalStateException("Assessment still answers; stop the test service before this phase");
      } catch (java.io.IOException expected) {
        checks.add(Map.of("case", "Assessment transport down", "actual", "connection failed"));
      }
    }
    publish(name, 503, "DEPENDENCY_UNAVAILABLE");
  }

  void recovery() throws Exception {
    draft("serviceDown"); draft("databaseDown");
    readyResponse("recovery"); publish("recovery", 200, null); learnerReads("recovery");
  }

  void learnerReads(String name) throws Exception {
    // A fresh learner avoids storing credentials and proves the public flow uses actual owner APIs.
    var learner = new ReadinessAcceptance(manifest, keys, privateKeys, origin);
    String email = "readiness-" + UUID.randomUUID() + "@example.com";
    String password = UUID.randomUUID() + "-Acceptance";
    learner.csrf();
    learner.gateway("POST", "/api/v1/auth/register", Map.of("email", email, "password", password,
        "fullName", "Readiness acceptance"), 201, "register learner");
    learner.login(email, password);
    learner.gateway("PUT", "/api/v1/me/learning-setup", Map.of("courseId", uuid(manifest, "courseId"),
        "levelId", uuid(manifest, "levelId")), 200, "learner setup");
    var lesson = learner.gateway("GET", "/api/v1/lessons/" + uuid(fixture(name), "lessonId"), null, 200, "learner reads " + name);
    require(fixture(name).get("exerciseId").equals(lesson.get("exerciseId")), "Learner exerciseId differs");
    learner.gateway("POST", "/api/v1/auth/logout", null, 204, "logout learner");
    checks.addAll(learner.checks);
  }
}
