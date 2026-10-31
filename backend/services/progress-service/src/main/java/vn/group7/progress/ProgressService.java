package vn.group7.progress;

import java.math.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.group7.core.*;

@Service
@Transactional
public class ProgressService {
  private final JdbcTemplate db;
  private final ProgressSources sources;
  private final Clock clock;

  public ProgressService(JdbcTemplate db, ProgressSources sources, Clock clock) {
    this.db = db;
    this.sources = sources;
    this.clock = clock;
  }

  public enum Outcome {
    APPLIED,
    DUPLICATE_EVENT,
    DUPLICATE_SUBMISSION
  }

  public Outcome consume(String raw, String key) {
    var e = CompletedEvent.parse(raw, key);
    db.update("INSERT INTO learner_lock(user_id) VALUES(?) ON CONFLICT DO NOTHING", e.userId());
    db.queryForMap("SELECT user_id FROM learner_lock WHERE user_id=? FOR UPDATE", e.userId());
    var processed =
        db.queryForList("SELECT payload_hash FROM processed_event WHERE event_id=?", e.eventId());
    if (!processed.isEmpty()) {
      sameHash(processed.getFirst().get("payload_hash"), e.payloadHash());
      return Outcome.DUPLICATE_EVENT;
    }
    db.update(
        "INSERT INTO processed_event(event_id,submission_id,payload_hash,processed_at)"
            + " VALUES(?,?,?,?)",
        e.eventId(),
        e.submissionId(),
        e.payloadHash(),
        Timestamp.from(clock.instant()));
    var history =
        db.queryForList(
            "SELECT payload_hash FROM learning_history WHERE submission_id=?", e.submissionId());
    if (!history.isEmpty()) {
      sameHash(history.getFirst().get("payload_hash"), e.payloadHash());
      return Outcome.DUPLICATE_SUBMISSION;
    }
    db.update(
        "INSERT INTO"
            + " learning_history(history_id,user_id,assessment_id,assessment_type,submission_id,result_id,course_id,level_id_at_start,lesson_id,earned_points,max_points,completed_at,payload_hash)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
        UUID.randomUUID(),
        e.userId(),
        e.assessmentId(),
        e.type(),
        e.submissionId(),
        e.resultId(),
        e.courseId(),
        e.levelId(),
        e.lessonId(),
        e.earned(),
        e.max(),
        Timestamp.from(e.completedAt()),
        e.payloadHash());
    if (e.type().equals("EXERCISE")) {
      var completions =
          db.queryForList(
              "SELECT * FROM lesson_completion WHERE user_id=? AND lesson_id=?",
              e.userId(),
              e.lessonId());
      if (completions.isEmpty())
        db.update(
            "INSERT INTO"
                + " lesson_completion(user_id,lesson_id,course_id,level_id_at_first_completion,first_submission_id,completed_at)"
                + " VALUES(?,?,?,?,?,?)",
            e.userId(),
            e.lessonId(),
            e.courseId(),
            e.levelId(),
            e.submissionId(),
            Timestamp.from(e.completedAt()));
      else {
        var c = completions.getFirst();
        if (!e.courseId().equals(c.get("course_id")))
          throw new IllegalArgumentException("Conflicting immutable Lesson/Course reference");
        Instant prior = instant(c.get("completed_at"));
        if (e.completedAt().isBefore(prior)
            || e.completedAt().equals(prior)
                && e.submissionId().toString().compareTo(c.get("first_submission_id").toString())
                    < 0)
          db.update(
              "UPDATE lesson_completion SET"
                  + " level_id_at_first_completion=?,first_submission_id=?,completed_at=? WHERE"
                  + " user_id=? AND lesson_id=?",
              e.levelId(),
              e.submissionId(),
              Timestamp.from(e.completedAt()),
              e.userId(),
              e.lessonId());
      }
      int count =
          db.queryForObject(
              "SELECT count(*) FROM lesson_completion WHERE user_id=? AND course_id=?",
              Integer.class,
              e.userId(),
              e.courseId());
      int changed =
          db.update(
              "UPDATE course_progress SET completed_lessons=?,updated_at=? WHERE user_id=? AND"
                  + " course_id=?",
              count,
              Timestamp.from(clock.instant()),
              e.userId(),
              e.courseId());
      if (changed == 0)
        db.update(
            "INSERT INTO course_progress(user_id,course_id,completed_lessons,updated_at)"
                + " VALUES(?,?,?,?)",
            e.userId(),
            e.courseId(),
            count,
            Timestamp.from(clock.instant()));
    }
    return Outcome.APPLIED;
  }

  private static void sameHash(Object previous, String current) {
    if (!current.equals(previous))
      throw new IllegalArgumentException(
          "Conflicting event/submission payload; requires review before replay");
  }

  private static Instant instant(Object value) {
    if (value instanceof Timestamp t) return t.toInstant();
    if (value instanceof OffsetDateTime t) return t.toInstant();
    throw new IllegalStateException("Unexpected timestamp");
  }

  private static BigDecimal decimal(Object value) {
    return new BigDecimal(value.toString());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> progress(UUID user, UUID course) {
    int total = sources.totalLessons(course);
    int completed =
        db.queryForObject(
            "SELECT count(*) FROM lesson_completion WHERE user_id=? AND course_id=?",
            Integer.class,
            user,
            course);
    if (completed > total) throw ApiError.dependency();
    BigDecimal percent =
        total == 0
            ? null
            : BigDecimal.valueOf(completed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    return Json.nullable(
        "courseId",
        course.toString(),
        "completedLessons",
        completed,
        "totalLessons",
        total,
        "progressPercent",
        percent,
        "generatedAt",
        clock.instant().toString(),
        "syncState",
        "EVENTUAL");
  }

  private static Map<String, Object> entry(Map<String, Object> row) {
    BigDecimal earned = decimal(row.get("earned_points")), max = decimal(row.get("max_points"));
    return Json.nullable(
        "historyId",
        row.get("history_id").toString(),
        "assessmentId",
        row.get("assessment_id").toString(),
        "assessmentType",
        row.get("assessment_type"),
        "submissionId",
        row.get("submission_id").toString(),
        "resultId",
        row.get("result_id").toString(),
        "courseId",
        row.get("course_id").toString(),
        "levelIdAtStart",
        row.get("level_id_at_start").toString(),
        "lessonId",
        row.get("lesson_id") == null ? null : row.get("lesson_id").toString(),
        "earnedPoints",
        earned,
        "maxPoints",
        max,
        "scorePercent",
        earned.multiply(BigDecimal.valueOf(100)).divide(max, 2, RoundingMode.HALF_UP),
        "completedAt",
        instant(row.get("completed_at")).toString());
  }

  @Transactional(readOnly = true)
  public Map<String, Object> history(UUID user, UUID course, String type, int page, int size) {
    if (page < 0 || page > 10000000 || size < 1 || size > 100)
      throw ApiError.bad("page", "Invalid pagination");
    if (type != null && !Set.of("EXERCISE", "QUIZ", "TEST").contains(type))
      throw ApiError.bad("type", "Unsupported type");
    String filter = " WHERE user_id=?";
    var args = new ArrayList<Object>();
    args.add(user);
    if (course != null) {
      filter += " AND course_id=?";
      args.add(course);
    }
    if (type != null) {
      filter += " AND assessment_type=?";
      args.add(type);
    }
    long count =
        db.queryForObject(
            "SELECT count(*) FROM learning_history" + filter, Long.class, args.toArray());
    args.add(size);
    args.add((long) page * size);
    var items =
        db
            .queryForList(
                "SELECT * FROM learning_history"
                    + filter
                    + " ORDER BY completed_at DESC,history_id LIMIT ? OFFSET ?",
                args.toArray())
            .stream()
            .map(ProgressService::entry)
            .toList();
    return Map.of(
        "items",
        items,
        "page",
        page,
        "size",
        size,
        "totalElements",
        count,
        "totalPages",
        (count + size - 1) / size);
  }

  @Transactional(readOnly = true)
  public Map<String, Object> dashboard(UUID user) {
    var setup = sources.setup(user);
    var course = setup.get("courseId");
    return Json.nullable(
        "setup",
        setup,
        "progress",
        course == null ? null : progress(user, Input.id(course.toString())),
        "recentHistory",
        history(user, null, null, 0, 5).get("items"),
        "generatedAt",
        clock.instant().toString(),
        "syncState",
        "EVENTUAL");
  }
}
