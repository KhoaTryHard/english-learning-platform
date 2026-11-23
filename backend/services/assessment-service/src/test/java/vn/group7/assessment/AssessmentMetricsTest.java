package vn.group7.assessment;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import vn.group7.core.*;

/** Internal statistics source for Progress (#36): GET /internal/v1/statistics/assessment-results. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AssessmentServiceTest.Config.class)
class AssessmentMetricsTest {
  static final String OTHER_COURSE = UUID.randomUUID().toString(),
      OTHER_LEVEL = UUID.randomUUID().toString();

  @Autowired AssessmentService service;
  @Autowired JdbcTemplate db;
  @Autowired AssessmentServiceTest.MutableClock clock;
  @Autowired AssessmentServiceTest.FakeContent content;

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
    content.level = AssessmentServiceTest.LEVEL;
  }

  private String assessment(String type, String course, int... points) {
    String id =
        service
            .create(
                Json.nullable(
                    "type",
                    type,
                    "courseId",
                    course,
                    "lessonId",
                    type.equals("EXERCISE") ? UUID.randomUUID().toString() : null,
                    "title",
                    type + " for metrics",
                    "timeLimitSeconds",
                    type.equals("EXERCISE") ? null : 60))
            .get("assessmentId")
            .toString();
    for (int point : points)
      service.putQuestion(
          id,
          null,
          Map.of(
              "questionType",
              "TRUE_FALSE",
              "content",
              "Is this correct?",
              "points",
              point,
              "correctAnswer",
              false));
    service.patch(id, Map.of("ready", true));
    return id;
  }

  /** Answers the first {@code correct} questions correctly, then submits. */
  private void attempt(String id, String user, int correct) {
    var start = service.start(id, user, UUID.randomUUID().toString());
    String attempt = start.get("attemptId").toString();
    @SuppressWarnings("unchecked")
    var questions = (List<Map<String, Object>>) start.get("questions");
    var answers = new ArrayList<Map<String, Object>>();
    for (int i = 0; i < questions.size(); i++)
      answers.add(
          Map.of(
              "questionId",
              questions.get(i).get("questionId"),
              "questionType",
              "TRUE_FALSE",
              "value",
              i >= correct));
    service.save(attempt, user, Map.of("answers", answers));
    service.submit(attempt, user, UUID.randomUUID().toString());
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> rows(Map<String, Object> body) {
    return (List<Map<String, Object>>) body.get("courses");
  }

  @Test
  void anEmptyDatabaseReturnsNoRowsRatherThanFabricatedZeros() {
    var body = service.metrics(null, null);
    assertTrue(rows(body).isEmpty());
    assertNotNull(body.get("generatedAt"));
  }

  @Test
  void everyStoredResultIsCountedIncludingRetakesAndTimeouts() {
    String exercise = assessment("EXERCISE", AssessmentServiceTest.COURSE, 2);
    attempt(exercise, AssessmentServiceTest.USER, 1);
    attempt(exercise, AssessmentServiceTest.USER, 1);

    String quiz = assessment("QUIZ", AssessmentServiceTest.COURSE, 2);
    String abandoned =
        service
            .start(quiz, AssessmentServiceTest.USER, UUID.randomUUID().toString())
            .get("attemptId")
            .toString();
    clock.now = clock.now.plusSeconds(120);
    // Reading the expired attempt finalizes it as TIMEOUT and stores a Result.
    assertEquals("TIMEOUT", service.attempt(abandoned, AssessmentServiceTest.USER).get("status"));

    var row = rows(service.metrics(null, null)).getFirst();
    assertEquals(AssessmentServiceTest.COURSE, row.get("courseId"));
    assertNull(row.get("levelId"));
    var counts = Json.obj(row.get("attemptCounts"));
    assertEquals(2, ((Number) counts.get("EXERCISE")).intValue());
    assertEquals(1, ((Number) counts.get("QUIZ")).intValue());
    assertEquals(0, ((Number) counts.get("TEST")).intValue());
    // The TIMEOUT Result counts towards the Quiz/Test denominator at zero points.
    assertEquals(1, ((Number) row.get("quizTestResultCount")).intValue());
    assertEquals(0, new BigDecimal(row.get("quizTestScoreSum").toString()).signum());
  }

  @Test
  void quizTestSumKeepsUnroundedRatiosAndExcludesExercise() {
    String exercise = assessment("EXERCISE", AssessmentServiceTest.COURSE, 2);
    attempt(exercise, AssessmentServiceTest.USER, 1);
    // Equal points keep the score independent of question ordering.
    String quiz = assessment("QUIZ", AssessmentServiceTest.COURSE, 1, 1, 1);
    // One of three points earned: 33.333...% must not be rounded by the source.
    attempt(quiz, AssessmentServiceTest.USER, 1);

    var row = rows(service.metrics(null, null)).getFirst();
    var sum = new BigDecimal(row.get("quizTestScoreSum").toString());
    assertEquals(1, ((Number) row.get("quizTestResultCount")).intValue());
    assertTrue(sum.stripTrailingZeros().scale() > 2, "source must not pre-round the ratio");
    assertEquals(new BigDecimal("33.33"), sum.setScale(2, RoundingMode.HALF_UP));
    assertEquals(1, ((Number) Json.obj(row.get("attemptCounts")).get("EXERCISE")).intValue());
  }

  @Test
  void withoutALevelFilterAllLevelsOfACourseCollapseIntoOneRow() {
    String quiz = assessment("QUIZ", AssessmentServiceTest.COURSE, 2);
    attempt(quiz, AssessmentServiceTest.USER, 2);
    content.level = OTHER_LEVEL;
    attempt(quiz, UUID.randomUUID().toString(), 0);

    var rows = rows(service.metrics(null, null));
    assertEquals(1, rows.size());
    assertNull(rows.getFirst().get("levelId"));
    assertEquals(2, ((Number) rows.getFirst().get("quizTestResultCount")).intValue());
  }

  @Test
  void levelFilterSelectsOnlyTheStartSnapshotLevel() {
    String quiz = assessment("QUIZ", AssessmentServiceTest.COURSE, 2);
    attempt(quiz, AssessmentServiceTest.USER, 2);
    content.level = OTHER_LEVEL;
    attempt(quiz, UUID.randomUUID().toString(), 0);

    var filtered =
        rows(service.metrics(AssessmentServiceTest.COURSE, AssessmentServiceTest.LEVEL));
    assertEquals(1, filtered.size());
    var row = filtered.getFirst();
    assertEquals(AssessmentServiceTest.LEVEL, row.get("levelId"));
    assertEquals(1, ((Number) row.get("quizTestResultCount")).intValue());
    assertEquals(new BigDecimal("100"), new BigDecimal(row.get("quizTestScoreSum").toString()).setScale(0, RoundingMode.HALF_UP));
  }

  @Test
  void courseFilterHidesOtherCoursesAndLevelRequiresCourse() {
    String here = assessment("QUIZ", AssessmentServiceTest.COURSE, 2);
    attempt(here, AssessmentServiceTest.USER, 2);
    String elsewhere = assessment("QUIZ", OTHER_COURSE, 2);
    attempt(elsewhere, AssessmentServiceTest.USER, 0);

    assertEquals(2, rows(service.metrics(null, null)).size());
    var selected = rows(service.metrics(OTHER_COURSE, null));
    assertEquals(1, selected.size());
    assertEquals(OTHER_COURSE, selected.getFirst().get("courseId"));

    assertEquals(
        400,
        assertThrows(ApiError.class, () -> service.metrics(null, AssessmentServiceTest.LEVEL))
            .status);
  }

  @Test
  void rowsAreOrderedStablyByCourseThenLevel() {
    String first = assessment("QUIZ", AssessmentServiceTest.COURSE, 2);
    attempt(first, AssessmentServiceTest.USER, 2);
    String second = assessment("QUIZ", OTHER_COURSE, 2);
    attempt(second, AssessmentServiceTest.USER, 2);

    var ids = rows(service.metrics(null, null)).stream().map(row -> row.get("courseId")).toList();
    var sorted = new ArrayList<>(ids);
    sorted.sort(Comparator.comparing(Object::toString));
    assertEquals(sorted, ids);
  }
}
