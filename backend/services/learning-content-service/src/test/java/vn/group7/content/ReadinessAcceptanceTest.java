package vn.group7.content;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.*;
import vn.group7.core.*;

/** Harness regressions use explicit HTTP stubs. They are never Assessment LIVE evidence. */
class ReadinessAcceptanceTest {
  HttpServer server;
  Map<String, Object> manifest;
  final Map<String, Boolean> published = new HashMap<>();
  int patchStatus = 200;
  boolean writesOnFailure;
  boolean wrongReadiness;
  boolean databaseDown;
  boolean autoStatus;

  @BeforeEach void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    manifest = new LinkedHashMap<>(Map.of("environment", "TEST", "gatewayUrl", base,
        "assessmentUrl", base, "assessmentUrlFromContent", base,
        "backendSha", "a".repeat(40), "courseId", UUID.randomUUID().toString(), "levelId", UUID.randomUUID().toString()));
    manifest.put("assessment", Map.of("kind", "LIVE", "sourceSha", "b".repeat(40), "image", "owner-test-image",
        "dbEngine", "owner-test-db", "migration", "test", "seedCommand", "test", "runtimeEvidence", "stub for driver unit tests only"));
    var fixtures = new LinkedHashMap<String, Object>();
    for (String name : ReadinessAcceptance.CASES) {
      String lesson = UUID.randomUUID().toString();
      fixtures.put(name, Json.nullable("lessonId", lesson, "exerciseId", UUID.randomUUID().toString(), "questionCount", 1));
      published.put(lesson, false);
    }
    ReadinessAcceptance.object(fixtures.get("missingMapping")).put("exerciseId", null);
    manifest.put("fixtures", fixtures);
    server.createContext("/", this::respond);
    server.start();
  }

  @AfterEach void stop() { if (server != null) server.stop(0); }

  ReadinessAcceptance driver() {
    return new ReadinessAcceptance(manifest, TestSupport.KEYS, TestSupport.KEYS, "http://localhost:5173");
  }

  String name(String lesson) {
    for (String name : ReadinessAcceptance.CASES)
      if (ReadinessAcceptance.object(ReadinessAcceptance.object(manifest.get("fixtures")).get(name)).get("lessonId").equals(lesson)) return name;
    throw new IllegalArgumentException();
  }

  void respond(HttpExchange exchange) throws java.io.IOException {
    int status = 200;
    Object body = Map.of();
    String path = exchange.getRequestURI().getPath();
    try {
      if (path.startsWith("/internal/v1/lessons/")) {
        try {
          var token = TestSupport.caller("assessment").verify(
              TokenCodec.bearer(exchange.getRequestHeaders().getFirst("Authorization")), "service", "assessment");
          if (!"content".equals(token.getSubject())) throw new ApiError(403, "FORBIDDEN", "caller");
          if (databaseDown) throw ApiError.dependency();
          String lesson = path.split("/")[4];
          var expected = driver().fixture(name(lesson));
          String caseName = name(lesson);
          boolean ready = !Set.of("missingMapping", "notReady").contains(caseName);
          body = Json.nullable("lessonId", wrongReadiness ? UUID.randomUUID().toString() : lesson,
              "ready", ready, "exerciseId", expected.get("exerciseId"), "questionCount", ready ? 1 : 0);
        } catch (ApiError error) { status = error.status; body = Map.of("code", error.code); }
      } else if (path.equals("/api/v1/auth/csrf")) body = Map.of("csrfToken", "test-csrf");
      else if (path.equals("/api/v1/auth/register")) status = 201;
      else if (path.equals("/api/v1/auth/logout")) status = 204;
      else if (path.startsWith("/api/v1/admin/lessons/")) {
        String lesson = path.substring(path.lastIndexOf('/') + 1);
        if (exchange.getRequestMethod().equals("PATCH")) {
          status = autoStatus ? (Set.of("missingMapping", "notReady").contains(name(lesson)) ? 409 : 200) : patchStatus;
          if (status == 200 || writesOnFailure) published.put(lesson, true);
          body = status == 200 ? Map.of("published", true) : Map.of("code",
              status == 409 ? "ASSESSMENT_NOT_READY" : "DEPENDENCY_UNAVAILABLE");
        } else body = Map.of("published", published.get(lesson));
      } else if (path.startsWith("/api/v1/lessons/")) {
        String lesson = path.substring(path.lastIndexOf('/') + 1);
        body = Map.of("exerciseId", driver().fixture(name(lesson)).get("exerciseId"));
      }
      byte[] bytes = Json.text(body).getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, status == 204 ? -1 : bytes.length);
      if (status != 204) exchange.getResponseBody().write(bytes);
    } finally { exchange.close(); }
  }

  @Test void provenanceAndSeparateFixturesAreRequired() {
    ReadinessAcceptance.validate(manifest, true);
    var assessment = new LinkedHashMap<>(ReadinessAcceptance.object(manifest.get("assessment")));
    manifest.put("assessment", assessment);
    assessment.put("kind", "MOCK");
    assertThrows(IllegalStateException.class, () -> ReadinessAcceptance.validate(manifest, true));
    assessment.put("kind", "LIVE"); assessment.put("sourceSha", "unknown");
    assertThrows(IllegalStateException.class, () -> ReadinessAcceptance.validate(manifest, true));
    assessment.put("sourceSha", "b".repeat(40));
    var fixtures = ReadinessAcceptance.object(manifest.get("fixtures"));
    ReadinessAcceptance.object(fixtures.get("notReady")).put("lessonId", driver().fixture("missingMapping").get("lessonId"));
    assertThrows(IllegalStateException.class, () -> ReadinessAcceptance.validate(manifest, true));
  }

  @Test void errorsCannotPassWhenLessonWasWritten() throws Exception {
    for (int status : List.of(409, 503)) {
      patchStatus = status;
      var driver = driver();
      driver.publish("notReady", status, status == 409 ? "ASSESSMENT_NOT_READY" : "DEPENDENCY_UNAVAILABLE");
      writesOnFailure = true;
      assertThrows(IllegalStateException.class, () -> driver.publish("notReady", status,
          status == 409 ? "ASSESSMENT_NOT_READY" : "DEPENDENCY_UNAVAILABLE"));
      published.put(ReadinessAcceptance.uuid(driver.fixture("notReady"), "lessonId"), false);
      writesOnFailure = false;
    }
  }

  @Test void sourceMismatchAndUnavailableDatabaseAreDetected() throws Exception {
    var driver = driver();
    driver.readyResponse("ready");
    wrongReadiness = true;
    assertThrows(IllegalStateException.class, () -> driver.readyResponse("ready"));
    wrongReadiness = false; databaseDown = true; patchStatus = 503;
    driver.outage(true);
    assertFalse(published.get(ReadinessAcceptance.uuid(driver.fixture("databaseDown"), "lessonId")));
  }

  @Test void onlineCoversSignedCallerRejectionAndLearnerRead() throws Exception {
    // Simulate Content's expected not-ready errors for those two fixtures.
    autoStatus = true;
    var driver = driver();
    driver.online();
    assertTrue(driver.checks.stream().anyMatch(c -> "invalid signature".equals(c.get("case")) && Integer.valueOf(401).equals(c.get("actual"))));
    assertTrue(driver.checks.stream().anyMatch(c -> "wrong caller".equals(c.get("case")) && Integer.valueOf(403).equals(c.get("actual"))));
    assertTrue(driver.checks.stream().anyMatch(c -> "learner reads ready".equals(c.get("case"))));
    assertTrue(driver.checks.toString().contains("logout learner"));
  }
}
