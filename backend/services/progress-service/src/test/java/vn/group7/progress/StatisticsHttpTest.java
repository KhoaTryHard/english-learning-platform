package vn.group7.progress;

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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.group7.core.*;

/** Admin statistics over the real signed Gateway assertion filter (#36, AC-18). */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProgressServiceTest.Config.class)
class StatisticsHttpTest {
  static final String PATH = "/api/v1/admin/statistics";

  @TempDir static Path keys;
  @Autowired StatisticsService statistics;
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
          keys.resolve(name + ".private.pem"),
          ProgressHttpTest.pem("PRIVATE", pair.getPrivate().getEncoded()));
      Files.writeString(
          keys.resolve(name + ".public.pem"),
          ProgressHttpTest.pem("PUBLIC", pair.getPublic().getEncoded()));
    }
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
    sources.down = false;
    sources.contextsDown = false;
    sources.cohortDown = false;
    sources.metricsDown = false;
    sources.contexts = ProgressServiceTest.FakeSources.defaultContexts();
    sources.members = new ArrayList<>();
    sources.metrics = new LinkedHashMap<>();
    sources.globalLearners = 3;
    api = new TokenCodec(keys.toString(), "api");
    mvc =
        MockMvcBuilders.standaloneSetup(new StatisticsController(statistics))
            .setControllerAdvice(new Errors())
            .addFilters(new ServiceGuard(new TokenCodec(keys.toString(), "progress"), "progress"))
            .build();
  }

  private MockHttpServletRequestBuilder signed(String role) {
    String assertion =
        api.sign(
            "principal",
            "progress",
            Map.of(
                "sub",
                UUID.randomUUID().toString(),
                "role",
                role,
                "sessionJti",
                UUID.randomUUID().toString(),
                "method",
                "GET",
                "path",
                PATH),
            Instant.now().plusSeconds(15));
    return get(PATH)
        .header("Authorization", "Bearer " + api.service("progress"))
        .header("X-Gateway-Principal", assertion);
  }

  @Test
  void onlyAdminReachesStatistics() throws Exception {
    mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    mvc.perform(signed("LEARNER")).andExpect(status().isForbidden());
    mvc.perform(signed("ADMIN")).andExpect(status().isOk());
  }

  @Test
  void adminResponseCarriesTheContractShape() throws Exception {
    mvc.perform(signed("ADMIN"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalLearners").value(3))
        .andExpect(jsonPath("$.syncState").value("EVENTUAL"))
        .andExpect(jsonPath("$.generatedAt").exists())
        .andExpect(jsonPath("$.courses.length()").value(1))
        .andExpect(jsonPath("$.courses[0].courseId").value(ProgressServiceTest.COURSE.toString()))
        .andExpect(jsonPath("$.courses[0].levelId").doesNotExist())
        .andExpect(jsonPath("$.courses[0].currentLearnerCount").value(0))
        .andExpect(jsonPath("$.courses[0].averageProgressPercent").doesNotExist())
        .andExpect(jsonPath("$.courses[0].completedLessonCount").value(0))
        .andExpect(jsonPath("$.courses[0].attemptCounts.EXERCISE").value(0))
        .andExpect(jsonPath("$.courses[0].attemptCounts.QUIZ").value(0))
        .andExpect(jsonPath("$.courses[0].attemptCounts.TEST").value(0))
        .andExpect(jsonPath("$.courses[0].averageQuizTestScorePercent").doesNotExist());
  }

  @Test
  void filterValidationMapsToTheApprovedStatusCodes() throws Exception {
    mvc.perform(signed("ADMIN").param("courseId", "not-a-uuid"))
        .andExpect(status().isBadRequest());
    mvc.perform(signed("ADMIN").param("levelId", ProgressServiceTest.LEVEL.toString()))
        .andExpect(status().isBadRequest());
    mvc.perform(signed("ADMIN").param("courseId", UUID.randomUUID().toString()))
        .andExpect(status().isNotFound());
    mvc.perform(
            signed("ADMIN")
                .param("courseId", ProgressServiceTest.COURSE.toString())
                .param("levelId", UUID.randomUUID().toString()))
        .andExpect(status().isBadRequest());
    mvc.perform(
            signed("ADMIN")
                .param("courseId", ProgressServiceTest.COURSE.toString())
                .param("levelId", ProgressServiceTest.LEVEL.toString()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.courses[0].levelId").value(ProgressServiceTest.LEVEL.toString()));
  }

  @Test
  void unavailableSourceReturns503() throws Exception {
    sources.metricsDown = true;
    mvc.perform(signed("ADMIN")).andExpect(status().isServiceUnavailable());
  }

  @Test
  void assertionForAnotherPathIsRejected() throws Exception {
    String assertion =
        api.sign(
            "principal",
            "progress",
            Map.of(
                "sub",
                UUID.randomUUID().toString(),
                "role",
                "ADMIN",
                "sessionJti",
                UUID.randomUUID().toString(),
                "method",
                "GET",
                "path",
                "/api/v1/me/dashboard"),
            Instant.now().plusSeconds(15));
    mvc.perform(
            get(PATH)
                .header("Authorization", "Bearer " + api.service("progress"))
                .header("X-Gateway-Principal", assertion))
        .andExpect(status().isUnauthorized());
  }
}
