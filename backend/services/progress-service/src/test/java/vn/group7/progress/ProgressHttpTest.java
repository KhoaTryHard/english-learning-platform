package vn.group7.progress;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.group7.core.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProgressServiceTest.Config.class)
class ProgressHttpTest {
  @TempDir static Path keys;
  @Autowired ProgressService service;
  @Autowired JdbcTemplate db;
  @Autowired ProgressServiceTest.FakeSources sources;
  MockMvc mvc;
  TokenCodec api;

  @BeforeAll
  static void keys() throws Exception {
    for (String name : List.of("api", "progress")) {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      var pair = generator.generateKeyPair();
      Files.writeString(
          keys.resolve(name + ".private.pem"), pem("PRIVATE", pair.getPrivate().getEncoded()));
      Files.writeString(
          keys.resolve(name + ".public.pem"), pem("PUBLIC", pair.getPublic().getEncoded()));
    }
  }

  static String pem(String type, byte[] bytes) {
    return "-----BEGIN "
        + type
        + " KEY-----\n"
        + Base64.getMimeEncoder(64, new byte[] {10}).encodeToString(bytes)
        + "\n-----END "
        + type
        + " KEY-----\n";
  }

  @BeforeEach
  void setup() {
    for (String table :
        List.of(
            "processed_event",
            "learning_history",
            "lesson_completion",
            "course_progress",
            "learner_lock")) db.update("DELETE FROM " + table);
    sources.total = 2;
    sources.down = false;
    sources.setup =
        Json.nullable(
            "courseId",
            ProgressServiceTest.COURSE.toString(),
            "levelId",
            ProgressServiceTest.LEVEL.toString(),
            "updatedAt",
            null);
    api = new TokenCodec(keys.toString(), "api");
    mvc =
        MockMvcBuilders.standaloneSetup(new ProgressController(service))
            .setControllerAdvice(new Errors())
            .addFilters(new ServiceGuard(new TokenCodec(keys.toString(), "progress"), "progress"))
            .build();
  }

  org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signed(
      String path, UUID user, String role) {
    String assertion =
        api.sign(
            "principal",
            "progress",
            Map.of(
                "sub",
                user.toString(),
                "role",
                role,
                "sessionJti",
                UUID.randomUUID().toString(),
                "method",
                "GET",
                "path",
                path),
            Instant.now().plusSeconds(15));
    return get(path)
        .header("Authorization", "Bearer " + api.service("progress"))
        .header("X-Gateway-Principal", assertion);
  }

  @Test
  void unsignedAndWrongRoleAreDenied() throws Exception {
    mvc.perform(get("/api/v1/me/history")).andExpect(status().isUnauthorized());
    mvc.perform(signed("/api/v1/me/history", ProgressServiceTest.USER, "ADMIN"))
        .andExpect(status().isForbidden());
  }

  @Test
  void realSignedPrincipalControlsOwnership() throws Exception {
    service.consume(
        Json.text(ProgressServiceTest.event("EXERCISE", ProgressServiceTest.LESSON)),
        ProgressServiceTest.USER.toString());
    mvc.perform(signed("/api/v1/me/history", ProgressServiceTest.USER, "LEARNER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(1));
    mvc.perform(
            signed("/api/v1/me/history", UUID.randomUUID(), "LEARNER")
                .param("userId", ProgressServiceTest.USER.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalElements").value(0));
    mvc.perform(
            signed("/api/v1/me/progress", ProgressServiceTest.USER, "LEARNER")
                .param("courseId", ProgressServiceTest.COURSE.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.progressPercent").value(50));
  }

  @Test
  void sourceFailureIs503WhileEmptyCourseIsNull() throws Exception {
    sources.down = true;
    mvc.perform(
            signed("/api/v1/me/progress", ProgressServiceTest.USER, "LEARNER")
                .param("courseId", ProgressServiceTest.COURSE.toString()))
        .andExpect(status().isServiceUnavailable());
    sources.down = false;
    sources.total = 0;
    var response =
        mvc.perform(
                signed("/api/v1/me/progress", ProgressServiceTest.USER, "LEARNER")
                    .param("courseId", ProgressServiceTest.COURSE.toString()))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(Json.map(response.getResponse().getContentAsString()).get("progressPercent"));
  }

  @Test
  void invalidPaginationAndIdsReturn400() throws Exception {
    mvc.perform(
            signed("/api/v1/me/history", ProgressServiceTest.USER, "LEARNER").param("size", "101"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            signed("/api/v1/me/progress", ProgressServiceTest.USER, "LEARNER")
                .param("courseId", "invalid"))
        .andExpect(status().isBadRequest());
    mvc.perform(signed("/api/v1/me/progress", ProgressServiceTest.USER, "LEARNER"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void assertionCannotBeReusedForAnotherPath() throws Exception {
    var assertion =
        api.sign(
            "principal",
            "progress",
            Map.of(
                "sub",
                ProgressServiceTest.USER.toString(),
                "role",
                "LEARNER",
                "sessionJti",
                UUID.randomUUID().toString(),
                "method",
                "GET",
                "path",
                "/api/v1/me/history"),
            Instant.now().plusSeconds(15));
    mvc.perform(
            get("/api/v1/me/dashboard")
                .header("Authorization", "Bearer " + api.service("progress"))
                .header("X-Gateway-Principal", assertion))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void consumerHasNoHttpIngress() throws Exception {
    mvc.perform(
            post("/internal/v1/events")
                .header("Authorization", "Bearer " + api.service("progress"))
                .contentType("application/json")
                .content(Json.text(ProgressServiceTest.event("QUIZ", null))))
        .andExpect(status().isForbidden());
  }

  @Test
  void dashboardWithoutSetupHasNullProgress() throws Exception {
    sources.setup = Json.nullable("courseId", null, "levelId", null, "updatedAt", null);
    var response =
        mvc.perform(signed("/api/v1/me/dashboard", ProgressServiceTest.USER, "LEARNER"))
            .andExpect(status().isOk())
            .andReturn();
    assertNull(Json.map(response.getResponse().getContentAsString()).get("progress"));
  }
}
