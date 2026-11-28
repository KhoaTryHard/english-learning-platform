package vn.group7.progress;

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
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import vn.group7.core.*;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProgressServiceTest.Config.class)
class ProgressServiceTest {
  static final UUID USER = UUID.randomUUID(),
      COURSE = UUID.randomUUID(),
      LEVEL = UUID.randomUUID(),
      LESSON = UUID.randomUUID();

  @Configuration
  @EnableTransactionManagement
  static class Config {
    @Bean
    DataSource dataSource() {
      var ds = new com.zaxxer.hikari.HikariDataSource();
      String url = System.getProperty("progress.test.jdbc-url");
      if (url == null) {
        ds.setJdbcUrl(
            "jdbc:h2:mem:progress;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
      } else {
        if (!url.matches("jdbc:postgresql://(?:localhost|127\\.0\\.0\\.1):[0-9]+/progress_test"))
          throw new IllegalArgumentException("Use dedicated localhost progress_test database");
        ds.setJdbcUrl(url);
        ds.setUsername(System.getProperty("progress.test.db-user", "progress"));
        ds.setPassword(System.getenv().getOrDefault("PROGRESS_TEST_DB_PASSWORD", ""));
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
    FakeSources sources() {
      return new FakeSources();
    }

    @Bean
    Clock clock() {
      return Clock.fixed(Instant.parse("2026-10-31T08:00:00Z"), ZoneOffset.UTC);
    }

    @Bean
    ProgressService service(JdbcTemplate db, FakeSources sources, Clock clock) {
      return new ProgressService(db, sources, clock);
    }
  }

  static class FakeSources extends ProgressSources {
    int total = 2;
    boolean down = false;
    Map<String, Object> setup =
        Json.nullable(
            "courseId", COURSE.toString(), "levelId", LEVEL.toString(), "updatedAt", null);

    FakeSources() {
      super(null, "", "");
    }

    @Override
    public int totalLessons(UUID course) {
      if (down) throw ApiError.dependency();
      return total;
    }

    @Override
    public Map<String, Object> setup(UUID user) {
      if (down) throw ApiError.dependency();
      return setup;
    }
  }

  @Autowired ProgressService service;
  @Autowired JdbcTemplate db;
  @Autowired FakeSources sources;

  @BeforeEach
  void reset() {
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
            "courseId", COURSE.toString(), "levelId", LEVEL.toString(), "updatedAt", null);
  }

  static Map<String, Object> event(String type, UUID lesson) {
    return Json.nullable(
        "eventId",
        UUID.randomUUID().toString(),
        "eventType",
        "AssessmentCompleted",
        "schemaVersion",
        1,
        "occurredAt",
        "2026-10-31T08:00:00Z",
        "correlationId",
        UUID.randomUUID().toString(),
        "payload",
        Json.nullable(
            "userId",
            USER.toString(),
            "assessmentId",
            UUID.randomUUID().toString(),
            "assessmentType",
            type,
            "submissionId",
            UUID.randomUUID().toString(),
            "resultId",
            UUID.randomUUID().toString(),
            "lessonId",
            lesson == null ? null : lesson.toString(),
            "courseId",
            COURSE.toString(),
            "levelIdAtStart",
            LEVEL.toString(),
            "earnedPoints",
            0,
            "maxPoints",
            2,
            "completedAt",
            "2026-10-31T08:00:00Z"));
  }

  static Map<String, Object> payload(Map<String, Object> e) {
    var p = Json.obj(e.get("payload"));
    e.put("payload", p);
    return p;
  }

  ProgressService.Outcome consume(Map<String, Object> e) {
    return service.consume(Json.text(e), USER.toString());
  }

  int count(String table) {
    return db.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }

  @Test
  void zeroDataEmptyCourseAndUnavailableSourceAreDistinct() {
    assertEquals(
        0.0, ((Number) service.progress(USER, COURSE).get("progressPercent")).doubleValue());
    sources.total = 0;
    assertNull(service.progress(USER, COURSE).get("progressPercent"));
    sources.down = true;
    assertEquals(503, assertThrows(ApiError.class, () -> service.progress(USER, COURSE)).status);
  }

  @Test
  void zeroScoreExerciseAndRetakeNeverDoubleCount() {
    consume(event("EXERCISE", LESSON));
    assertEquals(
        50.0, ((Number) service.progress(USER, COURSE).get("progressPercent")).doubleValue());
    consume(event("EXERCISE", LESSON));
    assertEquals(1, count("lesson_completion"));
    assertEquals(2, count("learning_history"));
    assertEquals(
        50.0, ((Number) service.progress(USER, COURSE).get("progressPercent")).doubleValue());
    consume(event("EXERCISE", UUID.randomUUID()));
    assertEquals(
        100.0, ((Number) service.progress(USER, COURSE).get("progressPercent")).doubleValue());
  }

  @Test
  void quizAndTestOnlyCreateHistory() {
    consume(event("QUIZ", LESSON));
    consume(event("TEST", null));
    assertEquals(0, count("lesson_completion"));
    assertEquals(2, count("learning_history"));
  }

  @Test
  void duplicateEventAndSubmissionAreSeparateDedupeBoundaries() {
    var e = event("EXERCISE", LESSON);
    assertEquals(ProgressService.Outcome.APPLIED, consume(e));
    assertEquals(ProgressService.Outcome.DUPLICATE_EVENT, consume(e));
    e.put("eventId", UUID.randomUUID().toString());
    assertEquals(ProgressService.Outcome.DUPLICATE_SUBMISSION, consume(e));
    assertEquals(2, count("processed_event"));
    assertEquals(1, count("learning_history"));
    assertEquals(1, count("lesson_completion"));
  }

  @Test
  void conflictingPayloadNeverOverwritesHistory() {
    var e = event("EXERCISE", LESSON);
    consume(e);
    e.put("eventId", UUID.randomUUID().toString());
    payload(e).put("earnedPoints", 1);
    assertThrows(IllegalArgumentException.class, () -> consume(e));
    assertEquals(1, count("processed_event"));
    assertEquals(1, count("learning_history"));
  }

  @Test
  void invalidSchemaKeyIdsAndScoreAreRejectedBeforeAnyWrite() {
    var cases = new ArrayList<Map<String, Object>>();
    var e = event("EXERCISE", null);
    cases.add(e);
    e = event("QUIZ", null);
    payload(e).put("earnedPoints", 3);
    cases.add(e);
    e = event("QUIZ", null);
    payload(e).put("userId", "not-uuid");
    cases.add(e);
    e = event("QUIZ", null);
    e.put("schemaVersion", 2);
    cases.add(e);
    for (var x : cases) assertThrows(IllegalArgumentException.class, () -> consume(x));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.consume(Json.text(event("QUIZ", null)), UUID.randomUUID().toString()));
    assertEquals(0, count("processed_event"));
  }

  @Test
  void progressWriteFailureRollsBackAllConsumerWrites() {
    db.execute(
        "ALTER TABLE course_progress ADD CONSTRAINT reject_test CHECK(completed_lessons=999)");
    try {
      assertThrows(Exception.class, () -> consume(event("EXERCISE", LESSON)));
      assertEquals(0, count("processed_event"));
      assertEquals(0, count("learning_history"));
      assertEquals(0, count("lesson_completion"));
    } finally {
      db.execute("ALTER TABLE course_progress DROP CONSTRAINT reject_test");
    }
  }

  @Test
  void courseLevelSnapshotsSurviveSetupChangeAndLateArrival() {
    var later = event("EXERCISE", LESSON);
    payload(later).put("levelIdAtStart", UUID.randomUUID().toString());
    payload(later).put("completedAt", "2026-10-31T09:00:00Z");
    consume(later);
    var earlier = event("EXERCISE", LESSON);
    consume(earlier);
    assertEquals(
        LEVEL,
        db.queryForObject(
            "SELECT level_id_at_first_completion FROM lesson_completion", UUID.class));
    sources.setup =
        Json.nullable(
            "courseId",
            UUID.randomUUID().toString(),
            "levelId",
            UUID.randomUUID().toString(),
            "updatedAt",
            null);
    assertEquals(
        50.0, ((Number) service.progress(USER, COURSE).get("progressPercent")).doubleValue());
    assertEquals(
        2, ((Number) service.history(USER, COURSE, null, 0, 20).get("totalElements")).intValue());
  }

  @Test
  void immutableLessonCourseConflictRollsBack() {
    consume(event("EXERCISE", LESSON));
    var other = event("EXERCISE", LESSON);
    payload(other).put("courseId", UUID.randomUUID().toString());
    assertThrows(IllegalArgumentException.class, () -> consume(other));
    assertEquals(1, count("learning_history"));
    assertEquals(1, count("processed_event"));
  }

  @Test
  void historyIsOwnerScopedFilteredAndPaged() {
    consume(event("EXERCISE", LESSON));
    consume(event("QUIZ", null));
    assertEquals(
        0,
        ((Number) service.history(UUID.randomUUID(), null, null, 0, 20).get("totalElements"))
            .intValue());
    assertEquals(
        1, ((Number) service.history(USER, COURSE, "QUIZ", 0, 1).get("totalElements")).intValue());
    assertEquals(1, ((List<?>) service.history(USER, null, null, 0, 1).get("items")).size());
    assertThrows(ApiError.class, () -> service.history(USER, null, null, -1, 20));
  }

  @Test
  void dashboardRetainsHistoryWithoutCurrentSetupAndLimitsToFive() {
    for (int i = 0; i < 7; i++) consume(event("QUIZ", null));
    sources.setup = Json.nullable("courseId", null, "levelId", null, "updatedAt", null);
    var dashboard = service.dashboard(USER);
    assertNull(dashboard.get("progress"));
    assertEquals(5, ((List<?>) dashboard.get("recentHistory")).size());
    sources.down = true;
    assertThrows(ApiError.class, () -> service.dashboard(USER));
  }

  @Test
  void concurrentReplayHasOneProjection() throws Exception {
    String raw = Json.text(event("EXERCISE", LESSON));
    try (var executor = Executors.newFixedThreadPool(4)) {
      var jobs = new ArrayList<Future<ProgressService.Outcome>>();
      for (int i = 0; i < 4; i++)
        jobs.add(executor.submit(() -> service.consume(raw, USER.toString())));
      int applied = 0;
      for (var job : jobs)
        if (job.get(15, TimeUnit.SECONDS) == ProgressService.Outcome.APPLIED) applied++;
      assertEquals(1, applied);
      assertEquals(1, count("learning_history"));
      assertEquals(1, count("lesson_completion"));
    }
  }

  @Test
  void fractionalScoresAreRetained() {
    var e = event("QUIZ", null);
    payload(e).put("earnedPoints", 1.5);
    payload(e).put("maxPoints", 2.5);
    consume(e);
    var item =
        Json.obj(((List<?>) service.history(USER, null, null, 0, 20).get("items")).getFirst());
    assertEquals(60.0, ((Number) item.get("scorePercent")).doubleValue());
  }

  @Test
  void schemaRejectsUnknownSensitiveFieldsAndNonUtc() {
    var e = event("QUIZ", null);
    payload(e).put("answerKey", "secret");
    assertThrows(IllegalArgumentException.class, () -> consume(e));
    var time = event("QUIZ", null);
    payload(time).put("completedAt", "2026-10-31T15:00:00+07:00");
    assertThrows(IllegalArgumentException.class, () -> consume(time));
  }
}
