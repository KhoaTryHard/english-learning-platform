package vn.group7.api;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.Cookie;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.*;
import vn.group7.core.*;

/** Real HTTP proxy tests; owner/session responses are explicit stubs, not LIVE services. */
class GatewayRoutingTest {
  @TempDir Path keys;
  TokenCodec tokens;
  Gateway gateway;
  HttpServer assessment, progress, content;
  final AtomicInteger contentCalls = new AtomicInteger();
  final AtomicInteger assessmentCalls = new AtomicInteger();
  final AtomicInteger progressCalls = new AtomicInteger();
  final List<String> attemptKeys = new ArrayList<>();
  final Map<String, String> attempts = new HashMap<>();
  String role, accountStatus;
  boolean sessionDown;
  boolean sessionRejected;
  Map<String, Object> sessionResponse;
  Map<String, Object> lastSessionCheck;

  @BeforeEach void start() throws Exception {
    for (String name : List.of("api", "user", "content", "assessment", "progress")) {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      var pair = generator.generateKeyPair();
      Files.writeString(keys.resolve(name + ".private.pem"), pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
      Files.writeString(keys.resolve(name + ".public.pem"), pem("PUBLIC KEY", pair.getPublic().getEncoded()));
    }
    tokens = new TokenCodec(keys.toString(), "api");
    role = "LEARNER"; accountStatus = "ACTIVE";
    content = server();
    content.createContext("/", exchange -> {
      contentCalls.incrementAndGet(); exchange.sendResponseHeaders(500, -1); exchange.close();
    });
    assessment = server(); progress = server();
    assessment.createContext("/", exchange -> {
      assessmentCalls.incrementAndGet();
      try {
        String bearer = exchange.getRequestHeaders().getFirst("Authorization").substring(7);
        assertEquals("api", new TokenCodec(keys.toString(), "assessment").verify(bearer, "service", "assessment").getSubject());
        var assertion = new TokenCodec(keys.toString(), "assessment").verify(exchange.getRequestHeaders().getFirst("X-Gateway-Principal"), "principal", "assessment");
        assertEquals(exchange.getRequestURI().getPath(), TokenCodec.string(assertion, "path"));
        assertNull(exchange.getRequestHeaders().getFirst("Cookie"));
        String key = exchange.getRequestHeaders().getFirst("Idempotency-Key");
        attemptKeys.add(key);
        byte[] body = Json.text(Map.of("attemptId", attempts.computeIfAbsent(key, k -> UUID.randomUUID().toString()))).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(201, body.length); exchange.getResponseBody().write(body);
      } finally { exchange.close(); }
    });
    progress.createContext("/", exchange -> {
      progressCalls.incrementAndGet();
      try {
        var principal = new TokenCodec(keys.toString(), "progress").verify(exchange.getRequestHeaders().getFirst("X-Gateway-Principal"), "principal", "progress");
        assertEquals(role, TokenCodec.string(principal, "role"));
        byte[] body = "{\"source\":\"progress-stub\"}".getBytes();
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body);
      } finally { exchange.close(); }
    });
    content.start(); assessment.start(); progress.start();
    OwnerClient sessions = new OwnerClient(tokens) {
      @Override public Map<String, Object> call(String base, String path, String owner, Map<String, Object> body) {
        lastSessionCheck = new LinkedHashMap<>(body);
        if (sessionDown) throw ApiError.dependency();
        if (sessionRejected) throw new ApiError(403, "CSRF_INVALID", "CSRF không hợp lệ");
        if (sessionResponse != null) return sessionResponse;
        return Map.of("userId", UUID.randomUUID().toString(), "jti", UUID.randomUUID().toString(),
            "role", role, "accountStatus", accountStatus, "expiresAt", Instant.now().plusSeconds(60).toString());
      }
    };
    gateway = new Gateway(tokens, sessions, "http://127.0.0.1:1", base(content), base(assessment), base(progress), "http://localhost:5173");
  }
  static HttpServer server() throws Exception { return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0); }
  static String base(HttpServer s) { return "http://127.0.0.1:" + s.getAddress().getPort(); }
  static String pem(String type, byte[] bytes) {
    return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, new byte[]{10}).encodeToString(bytes) + "\n-----END " + type + "-----\n";
  }
  @AfterEach void stop() { for (var s : List.of(content, assessment, progress)) if (s != null) s.stop(0); }

  MockHttpServletResponse request(String method, String path, String key, boolean cookie, String origin) throws Exception {
    var request = new MockHttpServletRequest(method, path);
    if (origin != null) request.addHeader("Origin", origin);
    if (cookie) request.setCookies(new Cookie("access_token", "opaque-user-token"), new Cookie("csrf_session", "session"));
    request.addHeader("X-CSRF-Token", "csrf");
    if (key != null) request.addHeader("Idempotency-Key", key);
    request.addHeader("Authorization", "Bearer browser-spoof");
    request.addHeader("X-Gateway-Principal", "browser-spoof");
    var response = new MockHttpServletResponse();
    gateway.proxy(request, response); return response;
  }
  MockHttpServletResponse request(String method, String path, String key) throws Exception {
    return request(method, path, key, true, "http://localhost:5173");
  }
  @Test void retryAndRetakeKeepKeysAndCorrectOwner() throws Exception {
    String path = "/api/v1/assessments/" + UUID.randomUUID() + "/attempts";
    var first = request("POST", path, "retry-key");
    var retry = request("POST", path, "retry-key");
    var retake = request("POST", path, "new-key");
    assertEquals(201, first.getStatus()); assertEquals(201, retry.getStatus()); assertEquals(201, retake.getStatus());
    assertEquals(first.getContentAsString(), retry.getContentAsString());
    assertNotEquals(first.getContentAsString(), retake.getContentAsString());
    assertEquals(List.of("retry-key", "retry-key", "new-key"), attemptKeys);
    assertEquals(0, contentCalls.get());
  }
  @Test void preflightAllowsIdempotencyWithoutSession() throws Exception {
    var response = request("OPTIONS", "/api/v1/assessments/" + UUID.randomUUID() + "/attempts", null, false, "http://localhost:5173");
    assertEquals(204, response.getStatus());
    assertTrue(response.getHeader("Access-Control-Allow-Headers").contains("Idempotency-Key"));
    assertEquals("http://localhost:5173", response.getHeader("Access-Control-Allow-Origin"));
    assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"));
  }
  @Test void progressAndStatisticsUseProgressOwner() throws Exception {
    assertEquals(200, request("GET", "/api/v1/me/progress", null).getStatus());
    assertEquals(403, request("GET", "/api/v1/admin/statistics", null).getStatus());
    role = "ADMIN";
    assertEquals(200, request("GET", "/api/v1/admin/statistics", null).getStatus());
    assertEquals(0, contentCalls.get());
  }
  @Test void missingCookieWrongOriginBlockedAndUnavailableFailClosed() throws Exception {
    assertEquals(401, request("GET", "/api/v1/me/progress", null, false, "http://localhost:5173").getStatus());
    assertEquals(403, request("GET", "/api/v1/me/progress", null, true, "https://evil.example").getStatus());
    accountStatus = "BLOCKED";
    assertEquals(503, request("GET", "/api/v1/me/progress", null).getStatus());
    accountStatus = "ACTIVE"; sessionDown = true;
    var unavailable = request("GET", "/api/v1/me/progress", null);
    assertEquals(503, unavailable.getStatus());
    assertFalse(unavailable.getContentAsString().contains("opaque-user-token"));
    String writePath = "/api/v1/assessments/" + UUID.randomUUID() + "/attempts";
    assertEquals(503, request("POST", writePath, "blocked-key").getStatus());
    sessionDown = false; sessionRejected = true;
    assertEquals(403, request("POST", writePath, "blocked-key").getStatus());
    assertEquals(0, assessmentCalls.get());
    assertEquals(0, progressCalls.get());
    assertEquals(0, contentCalls.get());
  }
  @Test void ownerDownDoesNotFallBackToContentAndInternalStaysPrivate() throws Exception {
    assessment.stop(0);
    var unavailable = request("GET", "/api/v1/assessments", null);
    assertEquals(503, unavailable.getStatus()); assertEquals(0, contentCalls.get());
    assertEquals(404, request("GET", "/internal/v1/courses/contexts", null).getStatus());
    assertEquals(405, request("DELETE", "/api/v1/admin/courses/" + UUID.randomUUID(), null).getStatus());
  }

  @Test void sessionValidationReceivesActualMethodAndCsrfContext() throws Exception {
    String path = "/api/v1/assessments/" + UUID.randomUUID() + "/attempts";
    assertEquals(201, request("POST", path, "method-context").getStatus());
    assertEquals(Map.of("token", "opaque-user-token", "requestMethod", "POST",
        "csrfSessionId", "session", "csrfToken", "csrf"), lastSessionCheck);
    assertEquals(200, request("GET", "/api/v1/me/progress", null).getStatus());
    assertEquals("GET", lastSessionCheck.get("requestMethod"));
    assertEquals("opaque-user-token", lastSessionCheck.get("token"));
  }

  @Test void malformedSuccessfulSessionResponsesNeverReachAnOwner() throws Exception {
    var valid = Map.<String, Object>of("userId", UUID.randomUUID().toString(),
        "jti", UUID.randomUUID().toString(), "role", "LEARNER", "accountStatus", "ACTIVE",
        "expiresAt", Instant.now().plusSeconds(60).toString());
    var invalid = new ArrayList<Map<String, Object>>();
    for (String field : valid.keySet()) {
      var missing = new LinkedHashMap<>(valid);
      missing.remove(field);
      invalid.add(missing);
    }
    for (var replacement : Map.<String, Object>of("userId", "invalid-id", "jti", "invalid-id",
        "role", "OWNER", "accountStatus", "BLOCKED", "expiresAt", "invalid-time").entrySet()) {
      var malformed = new LinkedHashMap<>(valid);
      malformed.put(replacement.getKey(), replacement.getValue());
      invalid.add(malformed);
    }
    var expired = new LinkedHashMap<>(valid);
    expired.put("expiresAt", Instant.now().minusSeconds(1).toString());
    invalid.add(expired);
    for (var value : invalid) {
      sessionResponse = value;
      var response = request("POST", "/api/v1/assessments/" + UUID.randomUUID() + "/attempts", "rejected");
      assertEquals(503, response.getStatus());
      assertEquals("DEPENDENCY_UNAVAILABLE", Json.map(response.getContentAsString()).get("code"));
      assertFalse(response.getContentAsString().contains("opaque-user-token"));
    }
    assertEquals(0, assessmentCalls.get());
    assertEquals(0, progressCalls.get());
    assertEquals(0, contentCalls.get());
  }
}
