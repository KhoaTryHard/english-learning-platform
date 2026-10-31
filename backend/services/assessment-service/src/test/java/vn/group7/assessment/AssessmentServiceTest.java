package vn.group7.assessment;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import vn.group7.core.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AssessmentServiceTest.Config.class)
class AssessmentServiceTest {
  static final String COURSE = UUID.randomUUID().toString(),
      LESSON = UUID.randomUUID().toString(),
      LEVEL = UUID.randomUUID().toString(),
      USER = UUID.randomUUID().toString();

  @Configuration
  @EnableTransactionManagement
  static class Config {
    @Bean
    DataSource dataSource() {
      var ds = new com.zaxxer.hikari.HikariDataSource();
      String url = System.getProperty("assessment.test.jdbc-url");
      if (url == null) {
        ds.setJdbcUrl(
            "jdbc:h2:mem:assessment;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
      } else {
        if (!url.matches("jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/assessment_test"))
          throw new IllegalArgumentException(
              "Tests require a dedicated localhost assessment_test database");
        ds.setJdbcUrl(url);
        ds.setUsername(System.getProperty("assessment.test.db-user", "assessment"));
        ds.setPassword(System.getenv().getOrDefault("ASSESSMENT_TEST_DB_PASSWORD", ""));
      }
      org.flywaydb.core.Flyway.configure().dataSource(ds).load().migrate();
      return ds;
    }

    @Bean
    JdbcTemplate db(DataSource ds) {
      return new JdbcTemplate(ds);
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource ds) {
      return new DataSourceTransactionManager(ds);
    }

    @Bean
    MutableClock clock() {
      return new MutableClock();
    }

    @Bean
    FakeContent content() {
      return new FakeContent();
    }

    @Bean
    AssessmentService service(JdbcTemplate db, FakeContent c, MutableClock clock) {
      return new AssessmentService(db, c, clock);
    }
  }

  static class MutableClock extends Clock {
    Instant now = Instant.parse("2026-10-29T10:00:00Z");

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }

  static class FakeContent extends ContentContext {
    boolean visible = true, unavailable = false;

    FakeContent() {
      super(null, "", "");
    }

    @Override
    public Map<String, Object> context(String course, String lesson) {
      if (unavailable) throw ApiError.dependency();
      return Map.of(lesson == null ? "published" : "visibleToLearner", visible);
    }

    @Override
    public String level(String user, String course) {
      if (unavailable) throw ApiError.dependency();
      return LEVEL;
    }
  }

  @Autowired AssessmentService service;
  @Autowired JdbcTemplate db;
  @Autowired MutableClock clock;
  @Autowired FakeContent content;

  @BeforeEach
  void reset() {
    for (String table :
        List.of(
            "outbox_event",
            "result",
            "submission",
            "question",
            "exercise_lesson",
            "assessment",
            "learner_lock")) db.update("DELETE FROM " + table);
    clock.now = Instant.parse("2026-10-29T10:00:00Z");
    content.visible = true;
    content.unavailable = false;
  }

  String create(String type) {
    return service
        .create(
            Json.nullable(
                "type",
                type,
                "courseId",
                COURSE,
                "lessonId",
                type.equals("EXERCISE") ? LESSON : null,
                "title",
                "Lesson assessment",
                "timeLimitSeconds",
                type.equals("EXERCISE") ? null : 60))
        .get("assessmentId")
        .toString();
  }

  Map<String, Object> tf(String id, boolean correct) {
    return service.putQuestion(
        id,
        null,
        Map.of(
            "questionType",
            "TRUE_FALSE",
            "content",
            "Is this correct?",
            "points",
            2,
            "correctAnswer",
            correct));
  }

  String ready() {
    String id = create("EXERCISE");
    tf(id, false);
    service.patch(id, Map.of("ready", true));
    return id;
  }

  String start(String id) {
    return service.start(id, USER, UUID.randomUUID().toString()).get("attemptId").toString();
  }

  String trace() {
    return UUID.randomUUID().toString();
  }

  @Test
  void readinessIsDatabaseOnlyAndDraftUntilExplicitlyReady() {
    String id = create("EXERCISE");
    assertEquals(false, service.readiness(LESSON).get("ready"));
    assertThrows(ApiError.class, () -> service.patch(id, Map.of("ready", true)));
    tf(id, false);
    content.unavailable = true;
    assertEquals(false, service.readiness(LESSON).get("ready"));
    service.patch(id, Map.of("ready", true));
    assertEquals(true, service.readiness(LESSON).get("ready"));
  }

  @Test
  void questionValidationRejectsUnknownFieldsZeroPointsAndInvalidOptions() {
    String id = create("EXERCISE");
    assertThrows(
        ApiError.class,
        () ->
            service.putQuestion(
                id,
                null,
                Map.of(
                    "questionType",
                    "TRUE_FALSE",
                    "content",
                    "Q",
                    "points",
                    0,
                    "correctAnswer",
                    false)));
    assertThrows(
        ApiError.class,
        () ->
            service.putQuestion(
                id,
                null,
                Map.of(
                    "questionType",
                    "TRUE_FALSE",
                    "content",
                    "Q",
                    "points",
                    1,
                    "correctAnswer",
                    false,
                    "role",
                    "ADMIN")));
    String option = trace();
    assertThrows(
        ApiError.class,
        () ->
            service.putQuestion(
                id,
                null,
                Map.of(
                    "questionType",
                    "SINGLE_CHOICE",
                    "content",
                    "Q",
                    "points",
                    1,
                    "options",
                    List.of(
                        Map.of("optionId", option, "text", "A"),
                        Map.of("optionId", option, "text", "B")),
                    "correctOptionId",
                    option)));
    assertEquals(0, db.queryForObject("SELECT count(*) FROM question", Integer.class));
  }

  @Test
  void questionCannotBeReplacedThroughAnotherAssessment() {
    String id = ready(), other = create("QUIZ");
    String q =
        service.get(id, true).get("questions") instanceof List<?> qs
            ? Validation.object(qs.getFirst()).get("questionId").toString()
            : null;
    assertThrows(
        ApiError.class,
        () ->
            service.putQuestion(
                other,
                q,
                Map.of(
                    "questionType",
                    "TRUE_FALSE",
                    "content",
                    "Q",
                    "points",
                    1,
                    "correctAnswer",
                    true)));
  }

  @Test
  void snapshotFalseAnswerAndRepeatedSubmitProduceOneAtomicOutbox() {
    String id = ready(), attempt = start(id);
    var initial = service.attempt(attempt, USER);
    assertFalse(Json.text(initial).contains("correctAnswer"));
    var q = Validation.object(((List<?>) initial.get("questions")).getFirst());
    service.putQuestion(
        id,
        q.get("questionId").toString(),
        Map.of(
            "questionType",
            "TRUE_FALSE",
            "content",
            "Changed",
            "points",
            100,
            "correctAnswer",
            true));
    service.save(
        attempt,
        USER,
        Map.of(
            "answers",
            List.of(
                Map.of(
                    "questionId",
                    q.get("questionId"),
                    "questionType",
                    "TRUE_FALSE",
                    "value",
                    false))));
    var result = service.submit(attempt, USER, trace());
    assertEquals(100.0, ((Number) result.get("scorePercent")).doubleValue());
    assertEquals(2, ((Number) result.get("maxPoints")).intValue());
    assertEquals(result.get("resultId"), service.submit(attempt, USER, trace()).get("resultId"));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM result", Integer.class));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM outbox_event", Integer.class));
    var event = Json.map(db.queryForObject("SELECT payload FROM outbox_event", String.class));
    assertEquals("AssessmentCompleted", event.get("eventType"));
    try (var stream =
        getClass().getResourceAsStream("/contracts/AssessmentCompleted_v1.schema.json")) {
      var schema =
          com.networknt.schema.JsonSchemaFactory.getInstance(
                  com.networknt.schema.SpecVersion.VersionFlag.V202012)
              .getSchema(Json.M.readTree(stream));
      assertEquals(Set.of(), schema.validate(Json.M.valueToTree(event)));
      var invalid = new LinkedHashMap<>(event);
      var badPayload = new LinkedHashMap<>(Validation.object(event.get("payload")));
      badPayload.put("lessonId", null);
      invalid.put("payload", badPayload);
      assertFalse(schema.validate(Json.M.valueToTree(invalid)).isEmpty());
    } catch (java.io.IOException error) {
      throw new AssertionError(error);
    }

    assertEquals(LESSON, Validation.object(event.get("payload")).get("lessonId"));
    assertFalse(Json.text(event).contains("correctAnswer"));
    assertEquals(USER, db.queryForObject("SELECT partition_key FROM outbox_event", String.class));
  }

  @Test
  void fullReplacementClearsAnswersAndZeroScoreRemainsValid() {
    String id = ready(), attempt = start(id);
    var q =
        Validation.object(((List<?>) service.attempt(attempt, USER).get("questions")).getFirst());
    service.save(
        attempt,
        USER,
        Map.of(
            "answers",
            List.of(
                Map.of(
                    "questionId",
                    q.get("questionId"),
                    "questionType",
                    "TRUE_FALSE",
                    "value",
                    false))));
    service.save(attempt, USER, Map.of("answers", List.of()));
    assertEquals(
        0.0, ((Number) service.submit(attempt, USER, trace()).get("scorePercent")).doubleValue());
  }

  @Test
  void retryRetakeAndRetryAfterUnpublish() {
    String id = ready(), key = trace();
    var first = service.start(id, USER, key);
    service.patch(id, Map.of("ready", false));
    content.visible = false;
    assertEquals(first.get("attemptId"), service.start(id, USER, key).get("attemptId"));
    assertThrows(ApiError.class, () -> service.start(id, USER, trace()));
    service.patch(id, Map.of("ready", true));
    content.visible = true;
    assertNotEquals(first.get("attemptId"), service.start(id, USER, trace()).get("attemptId"));
    String other = create("QUIZ");
    assertEquals(409, assertThrows(ApiError.class, () -> service.start(other, USER, key)).status);
  }

  @Test
  void ownershipAndUnknownAnswerAreRejected() {
    String attempt = start(ready());
    String other = trace();
    assertEquals(403, assertThrows(ApiError.class, () -> service.attempt(attempt, other)).status);
    assertThrows(
        ApiError.class,
        () ->
            service.save(
                attempt,
                USER,
                Map.of(
                    "answers",
                    List.of(
                        Map.of(
                            "questionId",
                            trace(),
                            "questionType",
                            "TRUE_FALSE",
                            "value",
                            false)))));
    assertThrows(
        ApiError.class,
        () ->
            service.save(
                attempt,
                USER,
                Map.of(
                    "answers",
                    List.of(
                        Map.of(
                            "questionId",
                            trace(),
                            "questionType",
                            "TRUE_FALSE",
                            "value",
                            "false")))));
  }

  @Test
  void timeoutUsesSavedAnswersAndNeverResetsDeadline() {
    String id = create("QUIZ");
    tf(id, true);
    service.patch(id, Map.of("ready", true));
    String attempt = start(id);
    Object deadline = service.attempt(attempt, USER).get("deadlineAt");
    clock.now = clock.now.plusSeconds(60);
    var late = service.save(attempt, USER, Map.of("answers", List.of()));
    assertEquals("TIMEOUT", late.get("status"));
    assertEquals(deadline, late.get("deadlineAt"));
    assertEquals("TIMEOUT", service.submit(attempt, USER, trace()).get("status"));
    assertEquals(1, db.queryForObject("SELECT count(*) FROM result", Integer.class));
  }

  @Test
  void exerciseHasNoDeadlineAndFinalizerWorksWithoutBrowser() {
    String exercise = start(ready());
    String quiz = create("TEST");
    tf(quiz, true);
    service.patch(quiz, Map.of("ready", true));
    String timed = start(quiz);
    clock.now = clock.now.plusSeconds(120);
    service.expireDue();
    assertNull(service.attempt(exercise, USER).get("deadlineAt"));
    assertEquals("IN_PROGRESS", service.attempt(exercise, USER).get("status"));
    assertEquals("TIMEOUT", service.attempt(timed, USER).get("status"));
  }

  @Test
  void dependencyFailureCreatesNoAttempt() {
    String id = ready();
    content.unavailable = true;
    assertEquals(503, assertThrows(ApiError.class, () -> start(id)).status);
    assertEquals(0, db.queryForObject("SELECT count(*) FROM submission", Integer.class));
  }

  @Test
  void outboxFailureRollsBackResultAndAttemptTransition() {
    String attempt = start(ready());
    db.execute("ALTER TABLE outbox_event ADD CONSTRAINT reject_test CHECK(schema_version=999)");
    try {
      assertThrows(Exception.class, () -> service.submit(attempt, USER, trace()));
      assertEquals(0, db.queryForObject("SELECT count(*) FROM result", Integer.class));
      assertEquals("IN_PROGRESS", service.attempt(attempt, USER).get("status"));
    } finally {
      db.execute("ALTER TABLE outbox_event DROP CONSTRAINT reject_test");
    }
  }

  @Test
  void concurrentStartAndSubmitAreIdempotent() throws Exception {
    String id = ready(), key = trace();
    try (var executor = Executors.newFixedThreadPool(4)) {
      var starts = new ArrayList<Future<Map<String, Object>>>();
      for (int i = 0; i < 4; i++) starts.add(executor.submit(() -> service.start(id, USER, key)));
      String attempt = starts.getFirst().get(15, TimeUnit.SECONDS).get("attemptId").toString();
      for (var f : starts) assertEquals(attempt, f.get(15, TimeUnit.SECONDS).get("attemptId"));
      var submits = new ArrayList<Future<Map<String, Object>>>();
      for (int i = 0; i < 4; i++)
        submits.add(executor.submit(() -> service.submit(attempt, USER, trace())));
      Object result = submits.getFirst().get(15, TimeUnit.SECONDS).get("resultId");
      for (var f : submits) assertEquals(result, f.get(15, TimeUnit.SECONDS).get("resultId"));
      assertEquals(1, db.queryForObject("SELECT count(*) FROM outbox_event", Integer.class));
    }
  }

  @Test
  void singleChoiceGradingRejectsForeignOptionAndUsesSnapshot() {
    String id = create("EXERCISE"), a = trace(), b = trace();
    var q =
        service.putQuestion(
            id,
            null,
            Map.of(
                "questionType",
                "SINGLE_CHOICE",
                "content",
                "Pick A",
                "points",
                3,
                "options",
                List.of(Map.of("optionId", a, "text", "A"), Map.of("optionId", b, "text", "B")),
                "correctOptionId",
                a));
    service.patch(id, Map.of("ready", true));
    String attempt = start(id);
    assertThrows(
        ApiError.class,
        () ->
            service.save(
                attempt,
                USER,
                Map.of(
                    "answers",
                    List.of(
                        Map.of(
                            "questionId",
                            q.get("questionId"),
                            "questionType",
                            "SINGLE_CHOICE",
                            "optionId",
                            trace())))));
    service.save(
        attempt,
        USER,
        Map.of(
            "answers",
            List.of(
                Map.of(
                    "questionId",
                    q.get("questionId"),
                    "questionType",
                    "SINGLE_CHOICE",
                    "optionId",
                    a))));
    assertEquals(
        100.0, ((Number) service.submit(attempt, USER, trace()).get("scorePercent")).doubleValue());
  }
}
