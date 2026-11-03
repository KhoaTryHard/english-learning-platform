package vn.group7.core;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.*;

class SecurityBoundaryTest {
  @TempDir Path keys;
  TokenCodec receiver;
  @BeforeEach void keys() throws Exception {
    for (String name : List.of("api", "user", "content", "assessment", "progress")) {
      var generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
      var pair = generator.generateKeyPair();
      Files.writeString(keys.resolve(name + ".private.pem"), pem("PRIVATE KEY", pair.getPrivate().getEncoded()));
      Files.writeString(keys.resolve(name + ".public.pem"), pem("PUBLIC KEY", pair.getPublic().getEncoded()));
    }
    receiver = new TokenCodec(keys.toString(), "user");
  }
  static String pem(String type, byte[] bytes) {
    return "-----BEGIN " + type + "-----\n" + Base64.getMimeEncoder(64, new byte[]{10}).encodeToString(bytes) + "\n-----END " + type + "-----\n";
  }
  TokenCodec caller(String name) { return new TokenCodec(keys.toString(), name); }
  MockHttpServletResponse internal(String path, String identity) throws Exception {
    var request = new MockHttpServletRequest("GET", path);
    request.addHeader("Authorization", "Bearer " + identity);
    var response = new MockHttpServletResponse();
    new ServiceGuard(receiver, "user").doFilter(request, response, (r, s) -> s.getWriter().write("owner-handler"));
    return response;
  }
  @Test void validProgressCanReadCohortButGatewayAndUserTokenCannot() throws Exception {
    assertEquals("owner-handler", internal("/internal/v1/learners/cohort", caller("progress").service("user")).getContentAsString());
    assertEquals(403, internal("/internal/v1/learners/cohort", caller("api").service("user")).getStatus());
    String userJwt = caller("user").sign("user", "api", Map.of("sub", UUID.randomUUID().toString(), "role", "LEARNER"), Instant.now().plusSeconds(60));
    assertEquals(401, internal("/internal/v1/learners/cohort", userJwt).getStatus());
  }
  @Test void onlyGatewayCanValidateSessionAndUnknownInternalRouteIsDenied() throws Exception {
    assertEquals("owner-handler", internal("/internal/v1/sessions/validate", caller("api").service("user")).getContentAsString());
    assertEquals(403, internal("/internal/v1/sessions/validate", caller("assessment").service("user")).getStatus());
    assertEquals(403, internal("/internal/v1/unknown", caller("progress").service("user")).getStatus());
  }
  @Test void wrongAudienceExpiredForgedAndUnknownIdentityAreDenied() throws Exception {
    assertEquals(401, internal("/internal/v1/learners/cohort", caller("progress").service("content")).getStatus());
    assertEquals(401, internal("/internal/v1/learners/cohort", caller("progress").sign("service", "user", Map.of(), Instant.now().minusSeconds(1))).getStatus());
    assertEquals(401, internal("/internal/v1/learners/cohort", "not-a-jwt").getStatus());
    String[] parts = caller("progress").service("user").split("\\.");
    var claims = Json.map(new String(Base64.getUrlDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8));
    claims.put("sub", "browser");
    String badSubject = parts[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(Json.text(claims).getBytes(java.nio.charset.StandardCharsets.UTF_8)) + "." + parts[2];
    assertEquals(401, internal("/internal/v1/learners/cohort", badSubject).getStatus());
  }
  @Test void missingKeysDenyAccessWithoutLeakingPathsOrToken() throws Exception {
    String token = caller("progress").service("user");
    Files.delete(keys.resolve("progress.public.pem"));
    var response = internal("/internal/v1/learners/cohort", token);
    assertEquals(401, response.getStatus());
    assertFalse(response.getContentAsString().contains(token));
    assertFalse(response.getContentAsString().contains(keys.toString()));
  }
}
