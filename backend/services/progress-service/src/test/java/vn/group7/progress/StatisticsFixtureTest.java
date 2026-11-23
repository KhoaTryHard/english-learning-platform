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
import vn.group7.core.Json;

/**
 * Fixture-driven acceptance for the six metric families (#36 checklist: "fixtures có kết quả tính
 * tay đối chiếu").
 *
 * <p>Every expected number lives in {@code fixtures/statistics-expected.json} next to the hand
 * arithmetic that produced it, so a reviewer can check the figures without reading Java. Symbolic
 * ids are mapped to random UUIDs here and completions are written through the real consumer.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProgressServiceTest.Config.class)
class StatisticsFixtureTest {
  @Autowired StatisticsService statistics;
  @Autowired ProgressService progress;
  @Autowired JdbcTemplate db;
  @Autowired ProgressServiceTest.FakeSources sources;

  Map<String, Object> fixture;
  Map<String, UUID> ids;

  private UUID id(String symbol) {
    return ids.computeIfAbsent(symbol, key -> UUID.randomUUID());
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> list(Object value) {
    return (List<Map<String, Object>>) value;
  }

  private static long number(Object value) {
    return new BigDecimal(value.toString()).longValueExact();
  }

  @BeforeEach
  void load() throws Exception {
    for (String table :
        List.of(
            "processed_event",
            "learning_history",
            "lesson_completion",
            "course_progress",
            "learner_lock")) db.update("DELETE FROM " + table);
    ids = new HashMap<>();
    try (var stream = getClass().getResourceAsStream("/fixtures/statistics-expected.json")) {
      assertNotNull(stream, "fixture statistics-expected.json must be on the test classpath");
      fixture = Json.map(new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
    }

    sources.down = false;
    sources.contextsDown = false;
    sources.cohortDown = false;
    sources.metricsDown = false;
    sources.globalLearners = number(fixture.get("totalLearnersGlobal"));

    sources.contexts = new ArrayList<>();
    var courses = Json.obj(fixture.get("courses"));
    for (var entry : courses.entrySet()) {
      var course = Json.obj(entry.getValue());
      var levels = new LinkedHashSet<UUID>();
      for (var level : (List<?>) course.get("levels")) levels.add(id(level.toString()));
      sources.contexts.add(
          new ProgressSources.CourseContext(
              id(entry.getKey()), (int) number(course.get("totalLessons")), Set.copyOf(levels)));
    }

    sources.members = new ArrayList<>();
    for (var member : list(fixture.get("cohort")))
      sources.members.add(
          new ProgressSources.CohortMember(
              id(member.get("learner").toString()),
              id(member.get("course").toString()),
              id(member.get("level").toString())));

    sources.metrics = new LinkedHashMap<>();
    var metrics = Json.obj(fixture.get("assessmentMetrics"));
    for (var entry : metrics.entrySet()) {
      var row = Json.obj(entry.getValue());
      var counts = Json.obj(row.get("attemptCounts"));
      var totals = new LinkedHashMap<String, Long>();
      for (String type : List.of("EXERCISE", "QUIZ", "TEST"))
        totals.put(type, number(counts.get(type)));
      // "C1" keys a Course row, "C1|L1" keys a Course+Level row.
      var parts = entry.getKey().split("\\|");
      String key =
          parts.length == 1 ? id(parts[0]).toString() : id(parts[0]) + "|" + id(parts[1]);
      sources.metrics.put(
          key,
          new ProgressSources.AssessmentMetrics(
              Map.copyOf(totals),
              new BigDecimal(row.get("quizTestScoreSum").toString()),
              number(row.get("quizTestResultCount"))));
    }

    // Completions are written through the real consumer, not seeded into the table.
    for (var completion : list(fixture.get("completions"))) {
      var learner = id(completion.get("learner").toString());
      var event = ProgressServiceTest.event("EXERCISE", id(completion.get("lesson").toString()));
      var payload = ProgressServiceTest.payload(event);
      payload.put("userId", learner.toString());
      payload.put("courseId", id(completion.get("course").toString()).toString());
      payload.put("levelIdAtStart", id(completion.get("level").toString()).toString());
      progress.consume(Json.text(event), learner.toString());
    }
  }

  private static void assertDecimal(Object expected, Object actual, String field) {
    if (expected == null) {
      assertNull(actual, field + " must be null, never a fabricated zero");
      return;
    }
    assertNotNull(actual, field);
    assertEquals(
        new BigDecimal(expected.toString()),
        new BigDecimal(actual.toString()),
        field + " (scale included)");
  }

  @TestFactory
  Collection<DynamicTest> everyFixtureCaseMatchesTheHandComputedNumbers() {
    var tests = new ArrayList<DynamicTest>();
    for (var testCase : list(fixture.get("cases"))) {
      String name = testCase.get("name").toString();
      tests.add(
          DynamicTest.dynamicTest(
              name,
              () -> {
                load();
                Object course = testCase.get("courseId"), level = testCase.get("levelId");
                var body =
                    statistics.statistics(
                        course == null ? null : id(course.toString()).toString(),
                        level == null ? null : id(level.toString()).toString());

                assertEquals(
                    number(testCase.get("totalLearners")),
                    number(body.get("totalLearners")),
                    name + ": totalLearners");
                assertEquals("EVENTUAL", body.get("syncState"), name + ": syncState");
                assertNotNull(body.get("generatedAt"), name + ": generatedAt");

                var expectedCourses = list(testCase.get("courses"));
                var actualCourses = list(body.get("courses"));
                assertEquals(expectedCourses.size(), actualCourses.size(), name + ": course count");

                for (var expected : expectedCourses) {
                  String courseId = id(expected.get("courseId").toString()).toString();
                  var actual =
                      actualCourses.stream()
                          .filter(row -> courseId.equals(row.get("courseId")))
                          .findFirst()
                          .orElseThrow(
                              () -> new AssertionError(name + ": missing course " + courseId));
                  String at = name + " / " + expected.get("courseId");

                  Object expectedLevel = expected.get("levelId");
                  assertEquals(
                      expectedLevel == null ? null : id(expectedLevel.toString()).toString(),
                      actual.get("levelId"),
                      at + ": levelId");
                  assertEquals(
                      number(expected.get("currentLearnerCount")),
                      number(actual.get("currentLearnerCount")),
                      at + ": currentLearnerCount");
                  assertEquals(
                      number(expected.get("completedLessonCount")),
                      number(actual.get("completedLessonCount")),
                      at + ": completedLessonCount");
                  assertDecimal(
                      expected.get("averageProgressPercent"),
                      actual.get("averageProgressPercent"),
                      at + ": averageProgressPercent");
                  assertDecimal(
                      expected.get("averageQuizTestScorePercent"),
                      actual.get("averageQuizTestScorePercent"),
                      at + ": averageQuizTestScorePercent");

                  var expectedCounts = Json.obj(expected.get("attemptCounts"));
                  var actualCounts = Json.obj(actual.get("attemptCounts"));
                  assertEquals(
                      Set.of("EXERCISE", "QUIZ", "TEST"),
                      actualCounts.keySet(),
                      at + ": attemptCounts keys");
                  for (String type : List.of("EXERCISE", "QUIZ", "TEST"))
                    assertEquals(
                        number(expectedCounts.get(type)),
                        number(actualCounts.get(type)),
                        at + ": attemptCounts." + type);
                }
              }));
    }
    return tests;
  }

  @Test
  void averagingRoundsOnceSoThePerLearnerRoundingBugStaysFixed() {
    // Fixture C1: 3 Lessons, cohort of 2, completions 2 and 0.
    // Correct: (66.666667 + 0) / 2 = 33.333333 -> 33.33
    // Rounding per learner first: (66.67 + 0.00) / 2 = 33.335 -> 33.34
    var row =
        list(statistics.statistics(null, null).get("courses")).stream()
            .filter(r -> id("C1").toString().equals(r.get("courseId")))
            .findFirst()
            .orElseThrow();
    assertEquals(new BigDecimal("33.33"), new BigDecimal(row.get("averageProgressPercent").toString()));
    assertNotEquals(
        new BigDecimal("33.34"),
        new BigDecimal(row.get("averageProgressPercent").toString()),
        "per-learner rounding would produce 33.34 and breaks DD-11");
  }
}
