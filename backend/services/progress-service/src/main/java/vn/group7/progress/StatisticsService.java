package vn.group7.progress;

import java.math.*;
import java.time.Clock;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.group7.core.*;

/**
 * W8-02/#36 — the six Admin metric families of DD-11.
 *
 * <p>Progress owns the published shape but owns none of the inputs: learner cohorts come from
 * User, Course/Level/Lesson structure from Content and Result aggregates from Assessment. Only
 * completions and history are local. A mandatory source that fails propagates 503 rather than a
 * zero, and an average without a denominator is published as null.
 */
@Service
public class StatisticsService {
  private final JdbcTemplate db;
  private final ProgressSources sources;
  private final Clock clock;

  public StatisticsService(JdbcTemplate db, ProgressSources sources, Clock clock) {
    this.db = db;
    this.sources = sources;
    this.clock = clock;
  }

  private static BigDecimal percent(BigDecimal numerator, BigDecimal denominator) {
    if (denominator.signum() <= 0) return null;
    var value = numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, MathContext.DECIMAL64);
    return clamp(value);
  }

  private static BigDecimal clamp(BigDecimal value) {
    var rounded = value.setScale(2, RoundingMode.HALF_UP);
    if (rounded.signum() < 0) return BigDecimal.ZERO.setScale(2);
    var hundred = BigDecimal.valueOf(100).setScale(2);
    return rounded.compareTo(hundred) > 0 ? hundred : rounded;
  }

  /** Completed first completions for a Course, optionally restricted to the Level snapshot. */
  private long completedLessons(UUID course, UUID level) {
    return level == null
        ? db.queryForObject(
            "SELECT count(*) FROM lesson_completion WHERE course_id=?", Long.class, course)
        : db.queryForObject(
            "SELECT count(*) FROM lesson_completion WHERE course_id=? AND"
                + " level_id_at_first_completion=?",
            Long.class,
            course,
            level);
  }

  /** Completed lessons per learner inside a Course, for the cohort average. */
  private Map<UUID, Long> completionsByLearner(UUID course, Collection<UUID> learners) {
    var counts = new HashMap<UUID, Long>();
    if (learners.isEmpty()) return counts;
    var ids = new ArrayList<>(learners);
    // Chunked IN-lists keep the statement under driver parameter limits for large cohorts.
    int chunk = 500;
    for (int from = 0; from < ids.size(); from += chunk) {
      var slice = ids.subList(from, Math.min(from + chunk, ids.size()));
      String placeholders = String.join(",", Collections.nCopies(slice.size(), "?"));
      var args = new ArrayList<Object>();
      args.add(course);
      args.addAll(slice);
      for (var row :
          db.queryForList(
              "SELECT user_id,count(*) AS completed FROM lesson_completion WHERE course_id=? AND"
                  + " user_id IN ("
                  + placeholders
                  + ") GROUP BY user_id",
              args.toArray()))
        counts.put(
            Input.id(row.get("user_id").toString()),
            new BigDecimal(row.get("completed").toString()).longValueExact());
    }
    return counts;
  }

  @Transactional(readOnly = true)
  public Map<String, Object> statistics(String courseId, String levelId) {
    UUID course = courseId == null ? null : Input.id(courseId),
        level = levelId == null ? null : Input.id(levelId);
    if (level != null && course == null)
      throw ApiError.bad("levelId", "levelId requires courseId");

    var contexts = sources.courseContexts();
    List<ProgressSources.CourseContext> selected;
    if (course == null) selected = contexts;
    else {
      selected =
          contexts.stream().filter(context -> context.courseId().equals(course)).toList();
      if (selected.isEmpty()) throw ApiError.missing();
      if (level != null && !selected.getFirst().levels().contains(level))
        throw ApiError.bad("levelId", "Level does not belong to the requested Course");
    }

    var cohort = sources.cohort(course, level);
    var metrics = sources.assessmentMetrics(course, level);

    var byCourse = new HashMap<UUID, List<UUID>>();
    for (var member : cohort.members())
      if (member.courseId() != null)
        byCourse.computeIfAbsent(member.courseId(), key -> new ArrayList<>()).add(member.userId());

    var rows = new ArrayList<Map<String, Object>>();
    for (var context : selected) {
      var learners = byCourse.getOrDefault(context.courseId(), List.of());
      var completions = completionsByLearner(context.courseId(), learners);
      BigDecimal averageProgress = null;
      if (!learners.isEmpty() && context.totalLessons() > 0) {
        var total = BigDecimal.ZERO;
        for (var learner : learners) {
          long completed = completions.getOrDefault(learner, 0L);
          // A learner cannot have completed more Lessons than the Course contains.
          if (completed > context.totalLessons()) throw ApiError.dependency();
          total =
              total.add(
                  percent(
                      BigDecimal.valueOf(completed), BigDecimal.valueOf(context.totalLessons())));
        }
        averageProgress = clamp(total.divide(BigDecimal.valueOf(learners.size()), MathContext.DECIMAL64));
      }

      String key =
          level == null ? context.courseId().toString() : context.courseId() + "|" + level;
      var assessment =
          metrics.getOrDefault(key, ProgressSources.AssessmentMetrics.empty());
      var attempts = new LinkedHashMap<String, Object>();
      for (String type : List.of("EXERCISE", "QUIZ", "TEST"))
        attempts.put(type, assessment.attemptCounts().getOrDefault(type, 0L));

      rows.add(
          Json.nullable(
              "courseId",
              context.courseId().toString(),
              "levelId",
              level == null ? null : level.toString(),
              "currentLearnerCount",
              (long) learners.size(),
              "averageProgressPercent",
              averageProgress,
              "completedLessonCount",
              completedLessons(context.courseId(), level),
              "attemptCounts",
              attempts,
              "averageQuizTestScorePercent",
              assessment.quizTestResultCount() == 0
                  ? null
                  : clamp(
                      assessment
                          .quizTestScoreSum()
                          .divide(
                              BigDecimal.valueOf(assessment.quizTestResultCount()),
                              MathContext.DECIMAL64))));
    }

    return Json.nullable(
        "totalLearners",
        cohort.totalLearnersGlobal(),
        "courses",
        rows,
        "generatedAt",
        clock.instant().toString(),
        "syncState",
        "EVENTUAL");
  }
}
