package vn.group7.api;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Real servlet dispatch: direct controller tests cannot detect intercepted OPTIONS. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"platform.frontend-origin=http://127.0.0.1:5173", "server.address=127.0.0.1"})
class GatewayPreflightHttpTest {
  @LocalServerPort int port;
  final HttpClient http = HttpClient.newHttpClient();

  HttpResponse<String> preflight(String origin, String path) throws Exception {
    return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
        .header("Origin", origin).header("Access-Control-Request-Method", "POST")
        .header("Access-Control-Request-Headers", "Idempotency-Key,X-CSRF-Token")
        .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test void browserCanPreflightAttemptWithCredentialsAndIdempotencyKey() throws Exception {
    var result = preflight("http://127.0.0.1:5173",
        "/api/v1/assessments/" + UUID.randomUUID() + "/attempts");
    assertEquals(204, result.statusCode());
    assertEquals("http://127.0.0.1:5173", result.headers().firstValue("Access-Control-Allow-Origin").orElseThrow());
    assertEquals("true", result.headers().firstValue("Access-Control-Allow-Credentials").orElseThrow());
    assertTrue(result.headers().firstValue("Access-Control-Allow-Headers").orElseThrow().contains("Idempotency-Key"));
    assertEquals("POST", result.headers().firstValue("Access-Control-Allow-Methods").orElseThrow());
  }

  @Test void foreignOriginAndUnknownPublicRouteAreRejected() throws Exception {
    assertEquals(403, preflight("http://invalid.example",
        "/api/v1/assessments/" + UUID.randomUUID() + "/attempts").statusCode());
    assertEquals(404, preflight("http://127.0.0.1:5173", "/api/v1/unknown").statusCode());
  }
}
