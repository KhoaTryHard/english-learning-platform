package vn.group7.assessment;

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
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.group7.core.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AssessmentServiceTest.Config.class)
class AssessmentHttpTest {
  @TempDir static Path keys;
  @Autowired AssessmentService service;
  @Autowired JdbcTemplate db;
  @Autowired AssessmentServiceTest.FakeContent content;
  MockMvc mvc;
  TokenCodec api, contentTokens, assessmentTokens;

  @BeforeAll
  static void keys() throws Exception {
    for (String name : List.of("api", "content", "assessment", "progress")) {
      var generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      var pair = generator.generateKeyPair();
      Files.writeString(
          keys.resolve(name + ".private.pem"), pem("PRIVATE", pair.getPrivate().getEncoded()));
      Files.writeString(
          keys.resolve(name + ".public.pem"), pem("PUBLIC", pair.getPublic().getEncoded()));
    }
  }

  static String pem(String type, byte[] value) {
    return "-----BEGIN "
        + type
        + " KEY-----\n"
        + Base64.getMimeEncoder(64, new byte[] {10}).encodeToString(value)
        + "\n-----END "
        + type
        + " KEY-----\n";
  }

  @BeforeEach
  void setup() {
    for (String table :
        List.of(
            "outbox_event",
            "result",
            "submission",
            "question",
            "exercise_lesson",
            "assessment",
            "learner_lock")) db.update("DELETE FROM " + table);
    content.visible = true;
    content.unavailable = false;
    api = new TokenCodec(keys.toString(), "api");
    contentTokens = new TokenCodec(keys.toString(), "content");
    assessmentTokens = new TokenCodec(keys.toString(), "assessment");
    mvc =
        MockMvcBuilders.standaloneSetup(new AssessmentController(service))
            .setControllerAdvice(new Errors())
            .addFilters(new ServiceGuard(assessmentTokens, "assessment"))
            .build();
  }

  String principal(String user, String role, String method, String path) {
    return api.sign(
        "principal",
        "assessment",
        Map.of(
            "sub",
            user,
            "role",
            role,
            "sessionJti",
            UUID.randomUUID().toString(),
            "method",
            method,
            "path",
            path),
        Instant.now().plusSeconds(15));
  }

  org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signed(
      org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder b,
      String method,
      String path,
      String role) {
    return b.header("Authorization", "Bearer " + api.service("assessment"))
        .header("X-Gateway-Principal", principal(AssessmentServiceTest.USER, role, method, path));
  }

  @Test
  void directBrowserAndSpoofedHeadersAreDenied() throws Exception {
    mvc.perform(
            get("/api/v1/assessments")
                .header("X-User-Id", AssessmentServiceTest.USER)
                .header("X-Role", "ADMIN"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void learnerCannotUseAdminEndpoints() throws Exception {
    String path = "/api/v1/admin/assessments";
    mvc.perform(signed(get(path), "GET", path, "LEARNER")).andExpect(status().isForbidden());
  }

  @Test
  void principalIsBoundToMethodPathAndAudience() throws Exception {
    String path = "/api/v1/assessments";
    for (String assertion :
        List.of(
            principal(AssessmentServiceTest.USER, "LEARNER", "POST", path),
            principal(AssessmentServiceTest.USER, "LEARNER", "GET", path + "/other"),
            api.sign(
                "principal",
                "content",
                Map.of(
                    "sub",
                    AssessmentServiceTest.USER,
                    "role",
                    "LEARNER",
                    "method",
                    "GET",
                    "path",
                    path),
                Instant.now().plusSeconds(15))))
      mvc.perform(
              get(path)
                  .header("Authorization", "Bearer " + api.service("assessment"))
                  .header("X-Gateway-Principal", assertion))
          .andExpect(status().isUnauthorized());
  }

  @Test
  void expiredAndWrongServiceAudienceAreRejected() throws Exception {
    String path = "/api/v1/assessments";
    mvc.perform(get(path).header("Authorization", "Bearer " + api.service("content")))
        .andExpect(status().isUnauthorized());
    mvc.perform(
            get(path)
                .header(
                    "Authorization",
                    "Bearer "
                        + api.sign(
                            "service", "assessment", Map.of(), Instant.now().minusSeconds(1))))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void readinessAllowsOnlyContentAndDoesNotCallContent() throws Exception {
    String path = "/internal/v1/lessons/" + AssessmentServiceTest.LESSON + "/exercise-readiness";
    content.unavailable = true;
    mvc.perform(get(path).header("Authorization", "Bearer " + contentTokens.service("assessment")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ready").value(false));
    mvc.perform(get(path).header("Authorization", "Bearer " + api.service("assessment")))
        .andExpect(status().isForbidden());
  }

  @Test
  void signedAdminCanCreateAndUnknownFieldsAreRejected() throws Exception {
    String path = "/api/v1/admin/assessments";
    var body =
        Json.nullable(
            "type",
            "EXERCISE",
            "courseId",
            AssessmentServiceTest.COURSE,
            "lessonId",
            AssessmentServiceTest.LESSON,
            "title",
            "Exercise",
            "timeLimitSeconds",
            null);
    mvc.perform(
            signed(post(path), "POST", path, "ADMIN")
                .contentType("application/json")
                .content(Json.text(body)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.ready").value(false));
    body.put("published", true);
    mvc.perform(
            signed(post(path), "POST", path, "ADMIN")
                .contentType("application/json")
                .content(Json.text(body)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
  }

  @Test
  void paginationAndMalformedIdsReturnContractErrors() throws Exception {
    String path = "/api/v1/assessments";
    mvc.perform(signed(get(path).param("size", "101"), "GET", path, "LEARNER"))
        .andExpect(status().isBadRequest());
    String bad = path + "/not-a-uuid";
    mvc.perform(signed(get(bad), "GET", bad, "LEARNER"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.traceId").exists());
  }

  @Test
  void startRequiresKeyAndNoBody() throws Exception {
    String path = "/api/v1/assessments/" + UUID.randomUUID() + "/attempts";
    mvc.perform(signed(post(path), "POST", path, "LEARNER")).andExpect(status().isBadRequest());
    mvc.perform(
            signed(post(path), "POST", path, "LEARNER")
                .header("Idempotency-Key", UUID.randomUUID())
                .contentType("application/json")
                .content("{} "))
        .andExpect(status().isBadRequest());
  }

  @Test
  void ownerDependencyFailureReturns503WithoutLeakingDetails() throws Exception {
    String path = "/api/v1/admin/assessments";
    content.unavailable = true;
    mvc.perform(
            signed(post(path), "POST", path, "ADMIN")
                .contentType("application/json")
                .content(
                    Json.text(
                        Json.nullable(
                            "type",
                            "EXERCISE",
                            "courseId",
                            AssessmentServiceTest.COURSE,
                            "lessonId",
                            AssessmentServiceTest.LESSON,
                            "title",
                            "Exercise",
                            "timeLimitSeconds",
                            null))))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM assessment", Integer.class));
  }

  @Test
  void signedExerciseFlowReturnsPrivateSafeAttemptAndStableResult() throws Exception {
    String adminPath = "/api/v1/admin/assessments";
    var create =
        Json.nullable(
            "type",
            "EXERCISE",
            "courseId",
            AssessmentServiceTest.COURSE,
            "lessonId",
            AssessmentServiceTest.LESSON,
            "title",
            "Exercise",
            "timeLimitSeconds",
            null);
    var created =
        mvc.perform(
                signed(post(adminPath), "POST", adminPath, "ADMIN")
                    .contentType("application/json")
                    .content(Json.text(create)))
            .andExpect(status().isCreated())
            .andReturn();
    String assessment =
        Json.map(created.getResponse().getContentAsString()).get("assessmentId").toString();
    String admin = adminPath + "/" + assessment, questionPath = admin + "/questions";
    var questionResponse =
        mvc.perform(
                signed(post(questionPath), "POST", questionPath, "ADMIN")
                    .contentType("application/json")
                    .content(
                        Json.text(
                            Map.of(
                                "questionType",
                                "TRUE_FALSE",
                                "content",
                                "False is a valid answer",
                                "points",
                                2,
                                "correctAnswer",
                                false))))
            .andExpect(status().isCreated())
            .andReturn();
    String question =
        Json.map(questionResponse.getResponse().getContentAsString()).get("questionId").toString();
    mvc.perform(
            signed(patch(admin), "PATCH", admin, "ADMIN")
                .contentType("application/json")
                .content("{\"ready\":true}"))
        .andExpect(status().isOk());
    String start = "/api/v1/assessments/" + assessment + "/attempts",
        key = UUID.randomUUID().toString();
    var started =
        mvc.perform(signed(post(start), "POST", start, "LEARNER").header("Idempotency-Key", key))
            .andExpect(status().isCreated())
            .andReturn();
    String attempt =
        Json.map(started.getResponse().getContentAsString()).get("attemptId").toString();
    assertFalse(started.getResponse().getContentAsString().contains("correctAnswer"));
    String save = "/api/v1/attempts/" + attempt + "/answers";
    mvc.perform(
            signed(put(save), "PUT", save, "LEARNER")
                .contentType("application/json")
                .content(
                    Json.text(
                        Map.of(
                            "answers",
                            List.of(
                                Map.of(
                                    "questionId",
                                    question,
                                    "questionType",
                                    "TRUE_FALSE",
                                    "value",
                                    false))))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("IN_PROGRESS"));
    String submit = "/api/v1/attempts/" + attempt + "/submit";
    var result =
        mvc.perform(signed(post(submit), "POST", submit, "LEARNER"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.scorePercent").value(100))
            .andExpect(jsonPath("$.progressSync").value("EVENTUAL"))
            .andReturn();
    String resultId =
        Json.map(result.getResponse().getContentAsString()).get("resultId").toString();
    mvc.perform(signed(post(submit), "POST", submit, "LEARNER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resultId").value(resultId));
    String read = "/api/v1/results/" + resultId;
    mvc.perform(signed(get(read), "GET", read, "LEARNER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.resultId").value(resultId));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM outbox_event", Integer.class));
  }

  @Test
  void explicitNullTypeReturns400AndMissingRoleReturns401() throws Exception {
    String path = "/api/v1/admin/assessments";
    mvc.perform(
            signed(post(path), "POST", path, "ADMIN")
                .contentType("application/json")
                .content(
                    Json.text(
                        Json.nullable(
                            "type",
                            null,
                            "courseId",
                            AssessmentServiceTest.COURSE,
                            "lessonId",
                            AssessmentServiceTest.LESSON,
                            "title",
                            "Exercise",
                            "timeLimitSeconds",
                            null))))
        .andExpect(status().isBadRequest());
    String principal =
        api.sign(
            "principal",
            "assessment",
            Map.of(
                "sub",
                AssessmentServiceTest.USER,
                "sessionJti",
                UUID.randomUUID().toString(),
                "method",
                "GET",
                "path",
                path),
            Instant.now().plusSeconds(15));
    mvc.perform(
            get(path)
                .header("Authorization", "Bearer " + api.service("assessment"))
                .header("X-Gateway-Principal", principal))
        .andExpect(status().isUnauthorized());
  }
}
