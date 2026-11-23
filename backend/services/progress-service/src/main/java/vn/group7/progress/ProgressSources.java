package vn.group7.progress;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.group7.core.*;

/**
 * Validated reads of the owner APIs Progress depends on. Every malformed or unreachable source
 * becomes 503 so Statistics never publishes a fabricated zero.
 */
@Component
public class ProgressSources {
  /** Maximum source pages read per request; guards against an unbounded paging loop. */
  private static final int MAX_PAGES = 1000;

  private static final int PAGE_SIZE = 100;

  private final OwnerClient client;
  private final String content, user, assessment;

  public ProgressSources(
      OwnerClient client,
      @Value("${platform.content-url}") String content,
      @Value("${platform.user-url}") String user,
      @Value("${platform.assessment-url}") String assessment) {
    this.client = client;
    this.content = content;
    this.user = user;
    this.assessment = assessment;
  }

  public int totalLessons(UUID course) {
    var value = client.get(content, "/internal/v1/courses/" + course + "/context", "content");
    try {
      if (!course.equals(Input.id(value.get("courseId").toString()))) throw ApiError.dependency();
      var number = value.get("totalLessons");
      if (!(number instanceof Number n)) throw ApiError.dependency();
      int total = new java.math.BigDecimal(n.toString()).intValueExact();
      if (total < 0) throw ApiError.dependency();
      return total;
    } catch (Exception e) {
      throw ApiError.dependency();
    }
  }

  public Map<String, Object> setup(UUID learner) {
    var value = client.get(user, "/internal/v1/users/" + learner + "/learning-setup", "user");
    try {
      if (!value.keySet().containsAll(Set.of("courseId", "levelId", "updatedAt")))
        throw ApiError.dependency();
      Object course = value.get("courseId"),
          level = value.get("levelId"),
          updated = value.get("updatedAt");
      if ((course == null) != (level == null)) throw ApiError.dependency();
      if (course != null) {
        course = Input.id(course.toString()).toString();
        level = Input.id(level.toString()).toString();
      }
      if (updated != null) updated = Instant.parse(updated.toString()).toString();
      return Json.nullable("courseId", course, "levelId", level, "updatedAt", updated);
    } catch (Exception e) {
      throw ApiError.dependency();
    }
  }

  /** One Course as published by Content, with every Level that belongs to it. */
  public record CourseContext(UUID courseId, int totalLessons, Set<UUID> levels) {}

  /** One LEARNER with the Course/Level currently selected in their learning setup. */
  public record CohortMember(UUID userId, UUID courseId, UUID levelId) {}

  /**
   * Learner cohort for the requested Course/Level filter.
   *
   * @param totalLearnersGlobal every LEARNER regardless of filter, used for the global metric
   * @param members the filtered cohort members
   */
  public record Cohort(long totalLearnersGlobal, List<CohortMember> members) {}

  private static long count(Object value) {
    if (!(value instanceof Number n)) throw ApiError.dependency();
    long count = new BigDecimal(n.toString()).longValueExact();
    if (count < 0) throw ApiError.dependency();
    return count;
  }

  private static UUID nullableId(Object value) {
    return value == null ? null : Input.id(value.toString());
  }

  @SuppressWarnings("unchecked")
  private static List<Object> array(Object value) {
    if (!(value instanceof List<?> list)) throw ApiError.dependency();
    return (List<Object>) list;
  }

  private static List<Object> items(Map<String, Object> page) {
    return array(page.get("items"));
  }

  /** Builds a query string from already-validated optional UUID filters. */
  private static String query(String path, Object... pairs) {
    var parts = new ArrayList<String>();
    for (int i = 0; i < pairs.length; i += 2)
      if (pairs[i + 1] != null) parts.add(pairs[i] + "=" + pairs[i + 1]);
    return parts.isEmpty() ? path : path + "?" + String.join("&", parts);
  }

  /** Reads every page of all persisted Courses, including unpublished and zero-Lesson Courses. */
  public List<CourseContext> courseContexts() {
    var all = new ArrayList<CourseContext>();
    try {
      long total = -1;
      for (int page = 0; page < MAX_PAGES; page++) {
        var body =
            client.get(
                content,
                query("/internal/v1/courses/contexts", "page", page, "size", PAGE_SIZE),
                "content");
        long reported = count(body.get("totalElements"));
        if (total >= 0 && reported != total) throw ApiError.dependency();
        total = reported;
        for (var item : items(body)) {
          var row = Json.obj(item);
          var levels = new LinkedHashSet<UUID>();
          if (!(row.get("levels") instanceof List<?> list)) throw ApiError.dependency();
          for (var level : list) {
            var value = Json.obj(level);
            if (!Input.id(row.get("courseId").toString())
                .equals(Input.id(value.get("courseId").toString()))) throw ApiError.dependency();
            levels.add(Input.id(value.get("levelId").toString()));
          }
          all.add(
              new CourseContext(
                  Input.id(row.get("courseId").toString()),
                  (int) count(row.get("totalLessons")),
                  Set.copyOf(levels)));
        }
        if (all.size() >= total) break;
        if (items(body).isEmpty()) throw ApiError.dependency();
      }
      if (all.size() != total) throw ApiError.dependency();
    } catch (ApiError e) {
      throw e;
    } catch (Exception e) {
      throw ApiError.dependency();
    }
    var distinct = new LinkedHashSet<UUID>();
    for (var row : all) if (!distinct.add(row.courseId())) throw ApiError.dependency();
    all.sort(Comparator.comparing(row -> row.courseId().toString()));
    return List.copyOf(all);
  }

  /**
   * Reads every page of the LEARNER cohort. The contract records multi-page cohort drift as an
   * open review item: {@code totalLearnersGlobal} is taken from the first page and the member list
   * is a best-effort snapshot, not one global transaction.
   */
  public Cohort cohort(UUID course, UUID level) {
    var seen = new LinkedHashSet<UUID>();
    var members = new ArrayList<CohortMember>();
    long global;
    try {
      long total = -1;
      Long first = null;
      for (int page = 0; page < MAX_PAGES; page++) {
        var body =
            client.get(
                user,
                query(
                    "/internal/v1/learners/cohort",
                    "page",
                    page,
                    "size",
                    PAGE_SIZE,
                    "courseId",
                    course,
                    "levelId",
                    level),
                "user");
        if (first == null) first = count(body.get("totalLearnersGlobal"));
        long reported = count(body.get("totalElements"));
        if (total >= 0 && reported != total) throw ApiError.dependency();
        total = reported;
        for (var item : items(body)) {
          var row = Json.obj(item);
          var id = Input.id(row.get("userId").toString());
          if (!row.containsKey("currentCourseId") || !row.containsKey("currentLevelId"))
            throw ApiError.dependency();
          var current = nullableId(row.get("currentCourseId"));
          var currentLevel = nullableId(row.get("currentLevelId"));
          if (course != null && !course.equals(current)) throw ApiError.dependency();
          if (level != null && !level.equals(currentLevel)) throw ApiError.dependency();
          if ((current == null) != (currentLevel == null)) throw ApiError.dependency();
          if (seen.add(id)) members.add(new CohortMember(id, current, currentLevel));
        }
        if (members.size() >= total) break;
        if (items(body).isEmpty()) throw ApiError.dependency();
      }
      if (members.size() != total) throw ApiError.dependency();
      global = first;
      if (global < total) throw ApiError.dependency();
    } catch (ApiError e) {
      throw e;
    } catch (Exception e) {
      throw ApiError.dependency();
    }
    return new Cohort(global, List.copyOf(members));
  }

  /** One Assessment metric row keyed by the Course/Level snapshot taken when an attempt started. */
  public record AssessmentMetrics(
      Map<String, Long> attemptCounts, BigDecimal quizTestScoreSum, long quizTestResultCount) {
    static AssessmentMetrics empty() {
      return new AssessmentMetrics(
          Map.of("EXERCISE", 0L, "QUIZ", 0L, "TEST", 0L), BigDecimal.ZERO, 0);
    }
  }

  /**
   * Reads Assessment result aggregates. The key is {@code courseId} when no Level filter is
   * applied, otherwise {@code courseId + "|" + levelId}.
   */
  public Map<String, AssessmentMetrics> assessmentMetrics(UUID course, UUID level) {
    var rows = new LinkedHashMap<String, AssessmentMetrics>();
    try {
      var body =
          client.get(
              assessment,
              query(
                  "/internal/v1/statistics/assessment-results",
                  "courseId",
                  course,
                  "levelId",
                  level),
              "assessment");
      for (var item : array(body.get("courses"))) {
        var row = Json.obj(item);
        var courseId = Input.id(row.get("courseId").toString());
        var levelId = nullableId(row.get("levelId"));
        if (course != null && !course.equals(courseId)) throw ApiError.dependency();
        if ((level == null) != (levelId == null)) throw ApiError.dependency();
        if (level != null && !level.equals(levelId)) throw ApiError.dependency();
        var counts = Json.obj(row.get("attemptCounts"));
        if (!counts.keySet().equals(Set.of("EXERCISE", "QUIZ", "TEST")))
          throw ApiError.dependency();
        var totals = new LinkedHashMap<String, Long>();
        for (String type : List.of("EXERCISE", "QUIZ", "TEST"))
          totals.put(type, count(counts.get(type)));
        long results = count(row.get("quizTestResultCount"));
        if (results > totals.get("QUIZ") + totals.get("TEST")) throw ApiError.dependency();
        if (!(row.get("quizTestScoreSum") instanceof Number sum)) throw ApiError.dependency();
        var score = new BigDecimal(sum.toString());
        if (score.signum() < 0) throw ApiError.dependency();
        if (results == 0 && score.signum() != 0) throw ApiError.dependency();
        String key = levelId == null ? courseId.toString() : courseId + "|" + levelId;
        if (rows.put(key, new AssessmentMetrics(Map.copyOf(totals), score, results)) != null)
          throw ApiError.dependency();
      }
    } catch (ApiError e) {
      throw e;
    } catch (Exception e) {
      throw ApiError.dependency();
    }
    return Map.copyOf(rows);
  }
}
