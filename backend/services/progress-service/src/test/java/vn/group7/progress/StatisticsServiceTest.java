package vn.group7.progress;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import vn.group7.core.*;

/** W8-02/#36 — the six Admin metric families over the real consumer-written tables. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProgressServiceTest.Config.class)
class StatisticsServiceTest {
  static final UUID OTHER_COURSE = UUID.randomUUID(),
      OTHER_LEVEL = UUID.randomUUID(),
      SECOND_LESSON = UUID.randomUUID();

  @Autowired StatisticsService statistics;
  @Autowired ProgressService progress;
  @Autowired JdbcTemplate db;
  @Autowired ProgressServiceTest.FakeSources sources;

  @BeforeEach
  void reset() {
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
    sources.globalLearners = 0;
  }

  /** Records a real EXERCISE completion through the consumer instead of seeding tables. */
  private void complete(UUID learner, UUID course, UUID level, UUID lesson) {
    var event = ProgressServiceTest.event("EXERCISE", lesson);
    var payload = ProgressServiceTest.payload(event);
    payload.put("userId", learner.toString());
    payload.put("courseId", course.toString());
    payload.put("levelIdAtStart", level.toString());
    progress.consume(Json.text(event), learner.toString());
  }

  private void enrol(UUID learner, UUID course, UUID level) {
    sources.members.add(new ProgressSources.CohortMember(learner, course, level));
    sources.globalLearners = Math.max(sources.globalLearners, sources.members.size());
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> courses(Map<String, Object> body) {
    return (List<Map<String, Object>>) body.get("courses");
  }

  private static Map<String, Object> row(Map<String, Object> body, UUID course) {
    return courses(body).stream()
        .filter(row -> course.toString().equals(row.get("courseId")))
        .findFirst()
        .orElseThrow();
  }

  @Test
  void unfilteredViewListsEveryCourseWithNullAveragesAndZeroCounts() {
    sources.contexts.add(new ProgressSources.CourseContext(OTHER_COURSE, 0, Set.of(OTHER_LEVEL)));
    sources.globalLearners = 7;

    var body = statistics.statistics(null, null);

    assertEquals(7L, body.get("totalLearners"));
    assertEquals("EVENTUAL", body.get("syncState"));
    assertNotNull(body.get("generatedAt"));
    assertEquals(2, courses(body).size());
    for (var row : courses(body)) {
      assertNull(row.get("levelId"));
      assertEquals(0L, row.get("currentLearnerCount"));
      // No cohort and no Result: averages must be null, never a fabricated zero.
      assertNull(row.get("averageProgressPercent"));
      assertNull(row.get("averageQuizTestScorePercent"));
      assertEquals(0L, row.get("completedLessonCount"));
      assertEquals(Map.of("EXERCISE", 0L, "QUIZ", 0L, "TEST", 0L), row.get("attemptCounts"));
    }
  }

  @Test
  void cohortAverageCountsLearnersWithoutCompletionsAsZero() {
    var first = UUID.randomUUID();
    var second = UUID.randomUUID();
    enrol(first, ProgressServiceTest.COURSE, ProgressServiceTest.LEVEL);
    enrol(second, ProgressServiceTest.COURSE, ProgressServiceTest.LEVEL);
    complete(first, ProgressServiceTest.COURSE, ProgressServiceTest.LEVEL, ProgressServiceTest.LESSON);

    var row = row(statistics.statistics(null, null), ProgressServiceTest.COURSE);

    assertEquals(2L, row.get("currentLearnerCount"));
    // (1/2 -> 50%) and (0/2 -> 0%) over two cohort learners.
    assertEquals(new BigDecimal("25.00"), row.get("averageProgressPercent"));
    assertEquals(1L, row.get("completedLessonCount"));
  }

  @Test
  void retakesDoNotInflateCompletionsWhileEveryResultIsCounted() {
    var learner = UUID.randomUUID();
    enrol(learner, ProgressServiceTest.COURSE, ProgressServiceTest.LEVEL);
    complete(learner, ProgressServiceTest.COURSE, ProgressServiceTest.LEVEL, ProgressServiceTest.LESSON);
    complete(learner, ProgressServiceTest.COURSE, ProgressServiceTest.LEVEL, ProgressServiceTest.LESSON);
    sources.metrics.put(
        ProgressServiceTest.COURSE.toString(),
        new ProgressSources.AssessmentMetrics(
            Map.of("EXERCISE", 2L, "QUIZ", 0L, "TEST", 0L), BigDecimal.ZERO, 0));

    var row = row(statistics.statistics(null, null), ProgressServiceTest.COURSE);

    assertEquals(1L, row.get("completedLessonCount"));
    assertEquals(new BigDecimal("50.00"), row.get("averageProgressPercent"));
    assertEquals(2L, Json.obj(row.get("attemptCounts")).get("EXERCISE"));
    assertNull(row.get("averageQuizTestScorePercent"));
  }

  @Test
  void quizTestAverageDividesTheUnroundedSumAndRoundsOnce() {
    sources.metrics.put(
        ProgressServiceTest.COURSE.toString(),
        new ProgressSources.AssessmentMetrics(
            Map.of("EXERCISE", 0L, "QUIZ", 2L, "TEST", 1L),
            new BigDecimal("200.0049999"),
            3));

    var row = row(statistics.statistics(null, null), ProgressServiceTest.COURSE);

    assertEquals(new BigDecimal("66.67"), row.get("averageQuizTestScorePercent"));
    assertEquals(Map.of("EXERCISE", 0L, "QUIZ", 2L, "TEST", 1L), row.get("attemptCounts"));
  }

  @Test
  void levelFilterUsesFirstCompletionSnapshotAndLevelKeyedMetrics() {
    var learner = UUID.randomUUID();
    var other = UUID.randomUUID();
    sources.contexts =
        new ArrayList<>(
            List.of(
                new ProgressSources.CourseContext(
                    ProgressServiceTest.COURSE, 2, Set.of(ProgressServiceTest.LEVEL, OTHER_LEVEL))));
    enrol(learner, ProgressServiceTest.COURSE, ProgressServiceTest.LEVEL);
    enrol(other, ProgressServiceTest.COURSE, OTHER_LEVEL);
    complete(learner, ProgressServiceTest.COURSE, ProgressServiceTest.LEVEL, ProgressServiceTest.LESSON);
    complete(other, ProgressServiceTest.COURSE, OTHER_LEVEL, SECOND_LESSON);
    sources.metrics.put(
        ProgressServiceTest.COURSE + "|" + ProgressServiceTest.LEVEL,
        new ProgressSources.AssessmentMetrics(
            Map.of("EXERCISE", 1L, "QUIZ", 0L, "TEST", 0L), BigDecimal.ZERO, 0));

    var filtered =
        statistics.statistics(
            ProgressServiceTest.COURSE.toString(), ProgressServiceTest.LEVEL.toString());
    var row = row(filtered, ProgressServiceTest.COURSE);

    assertEquals(1, courses(filtered).size());
    assertEquals(ProgressServiceTest.LEVEL.toString(), row.get("levelId"));
    assertEquals(1L, row.get("currentLearnerCount"));
    // Only the completion whose first-completion snapshot carries this Level is counted.
    assertEquals(1L, row.get("completedLessonCount"));
    assertEquals(1L, Json.obj(row.get("attemptCounts")).get("EXERCISE"));
    // Denominator stays all Course Lessons even under a Level filter.
    assertEquals(new BigDecimal("50.00"), row.get("averageProgressPercent"));

    var unfiltered = row(statistics.statistics(null, null), ProgressServiceTest.COURSE);
    assertEquals(2L, unfiltered.get("completedLessonCount"));
    assertEquals(0L, Json.obj(unfiltered.get("attemptCounts")).get("EXERCISE"));
  }

  @Test
  void invalidFiltersAreRejectedBeforeAnySourceCall() {
    assertEquals(
        400,
        assertThrows(
                ApiError.class,
                () -> statistics.statistics(null, ProgressServiceTest.LEVEL.toString()))
            .status);
    assertEquals(
        400, assertThrows(ApiError.class, () -> statistics.statistics("not-a-uuid", null)).status);
    assertEquals(
        404,
        assertThrows(
                ApiError.class, () -> statistics.statistics(UUID.randomUUID().toString(), null))
            .status);
    assertEquals(
        400,
        assertThrows(
                ApiError.class,
                () ->
                    statistics.statistics(
                        ProgressServiceTest.COURSE.toString(), OTHER_LEVEL.toString()))
            .status);
  }

  @Test
  void anyMandatorySourceFailureBecomes503() {
    sources.contextsDown = true;
    assertEquals(503, assertThrows(ApiError.class, () -> statistics.statistics(null, null)).status);
    sources.contextsDown = false;
    sources.cohortDown = true;
    assertEquals(503, assertThrows(ApiError.class, () -> statistics.statistics(null, null)).status);
    sources.cohortDown = false;
    sources.metricsDown = true;
    assertEquals(503, assertThrows(ApiError.class, () -> statistics.statistics(null, null)).status);
  }

  @Test
  void completionsExceedingCourseLessonsAreReportedAsInconsistentSource() {
    var learner = UUID.randomUUID();
    enrol(learner, ProgressServiceTest.COURSE, ProgressServiceTest.LEVEL);
    complete(learner, ProgressServiceTest.COURSE, ProgressServiceTest.LEVEL, ProgressServiceTest.LESSON);
    complete(learner, ProgressServiceTest.COURSE, ProgressServiceTest.LEVEL, SECOND_LESSON);
    sources.contexts =
        new ArrayList<>(
            List.of(
                new ProgressSources.CourseContext(
                    ProgressServiceTest.COURSE, 1, Set.of(ProgressServiceTest.LEVEL))));

    assertEquals(503, assertThrows(ApiError.class, () -> statistics.statistics(null, null)).status);
  }
}
