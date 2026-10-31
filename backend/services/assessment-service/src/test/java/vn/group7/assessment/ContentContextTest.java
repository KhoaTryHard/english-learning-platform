package vn.group7.assessment;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import vn.group7.core.*;

class ContentContextTest {
  @TempDir Path keys;
  HttpServer server;
  ContentContext context;
  String course = UUID.randomUUID().toString(),
      lesson = UUID.randomUUID().toString(),
      level = UUID.randomUUID().toString();
  final Map<String, Map<String, Object>> responses = new HashMap<>();
  AtomicInteger failure = new AtomicInteger(0);
  AtomicInteger calls = new AtomicInteger(0);

  @BeforeEach
  void setup() throws Exception {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    var pair = generator.generateKeyPair();
    Files.writeString(
        keys.resolve("assessment.private.pem"),
        AssessmentHttpTest.pem("PRIVATE", pair.getPrivate().getEncoded()));
    Files.writeString(
        keys.resolve("assessment.public.pem"),
        AssessmentHttpTest.pem("PUBLIC", pair.getPublic().getEncoded()));
    var tokens = new TokenCodec(keys.toString(), "assessment");
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          calls.incrementAndGet();
          try {
            String owner =
                exchange.getRequestURI().getPath().contains("/users/") ? "user" : "content";
            var identity =
                tokens.verify(
                    TokenCodec.bearer(exchange.getRequestHeaders().getFirst("Authorization")),
                    "service",
                    owner);
            if (!"assessment".equals(identity.getSubject())) throw new IllegalStateException();
            var payload = responses.get(exchange.getRequestURI().getPath());
            int status = failure.get() != 0 ? failure.get() : payload == null ? 404 : 200;
            byte[] bytes =
                Json.text(payload == null ? Map.of() : payload)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
          } catch (Exception e) {
            exchange.sendResponseHeaders(500, -1);
          } finally {
            exchange.close();
          }
        });
    server.start();
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    context = new ContentContext(new OwnerClient(tokens), base, base);
  }

  @AfterEach
  void stop() {
    server.stop(0);
  }

  @Test
  void signedOwnerCallsValidateSetupAndLevel() {
    String user = UUID.randomUUID().toString();
    responses.put(
        "/internal/v1/users/" + user + "/learning-setup",
        Map.of("courseId", course, "levelId", level));
    responses.put(
        "/internal/v1/courses/" + course + "/context",
        Map.of("courseId", course, "published", true, "levels", List.of(Map.of("levelId", level))));
    responses.put(
        "/internal/v1/lessons/" + lesson + "/context",
        Map.of("courseId", course, "lessonId", lesson, "visibleToLearner", true));
    assertEquals(true, context.context(course, lesson).get("visibleToLearner"));
    assertEquals(level, context.level(user, course));
    assertEquals(3, calls.get());
  }

  @Test
  void malformedOrUnavailableSourceFailsClosed() {
    responses.put("/internal/v1/courses/" + course + "/context", Map.of("courseId", course));
    assertEquals(503, assertThrows(ApiError.class, () -> context.context(course, null)).status);
    failure.set(503);
    assertEquals(503, assertThrows(ApiError.class, () -> context.context(course, lesson)).status);
  }

  @Test
  void wrongSetupNeverInventsLevel() {
    String user = UUID.randomUUID().toString();
    responses.put(
        "/internal/v1/users/" + user + "/learning-setup",
        Map.of("courseId", UUID.randomUUID().toString(), "levelId", level));
    assertEquals(409, assertThrows(ApiError.class, () -> context.level(user, course)).status);
  }
}
