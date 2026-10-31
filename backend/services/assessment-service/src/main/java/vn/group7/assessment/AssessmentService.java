package vn.group7.assessment;

import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.group7.core.*;

@Service
@Transactional
public class AssessmentService {
  private final JdbcTemplate db;
  private final ContentContext content;
  private final Clock clock;

  public AssessmentService(JdbcTemplate db, ContentContext content, Clock clock) {
    this.db = db;
    this.content = content;
    this.clock = clock;
  }

  private static String str(Object v) {
    return v == null ? null : v.toString();
  }

  private static UUID uuid(Object v) {
    return Input.id(str(v));
  }

  private Map<String, Object> one(String sql, Object... args) {
    var rows = db.queryForList(sql, args);
    if (rows.isEmpty()) throw ApiError.missing();
    return rows.getFirst();
  }

  private Map<String, Object> assessment(String id, boolean lock) {
    return one(
        "SELECT * FROM assessment WHERE assessment_id=?" + (lock ? " FOR UPDATE" : ""), uuid(id));
  }

  private List<Map<String, Object>> questions(String id) {
    return db
        .queryForList(
            "SELECT definition FROM question WHERE assessment_id=? ORDER BY question_id", uuid(id))
        .stream()
        .map(x -> Json.map(str(x.get("definition"))))
        .toList();
  }

  private Map<String, Object> metadata(Map<String, Object> row) {
    return Json.nullable(
        "assessmentId",
        str(row.get("assessment_id")),
        "type",
        row.get("type"),
        "courseId",
        str(row.get("course_id")),
        "lessonId",
        str(row.get("lesson_id")),
        "title",
        row.get("title"),
        "timeLimitSeconds",
        row.get("time_limit_seconds"),
        "ready",
        row.get("ready"),
        "questionCount",
        questions(str(row.get("assessment_id"))).size());
  }

  private static Integer time(String type, Object seconds) {
    if (type.equals("EXERCISE")) {
      if (seconds != null) throw ApiError.bad("timeLimitSeconds", "Exercise has no timer");
      return null;
    }
    int n = Validation.integer(seconds);
    if (n <= 0) throw ApiError.bad("timeLimitSeconds", "Positive time limit required");
    return n;
  }

  public Map<String, Object> create(Map<String, Object> b) {
    var fields = Set.of("type", "courseId", "lessonId", "title", "timeLimitSeconds");
    Validation.fields(b, fields, fields);
    String type = str(b.get("type"));
    if (type == null || !Validation.TYPES.contains(type))
      throw ApiError.bad("type", "Unsupported assessment type");
    String course = Validation.uuid(b.get("courseId")),
        lesson = Validation.nullableId(b.get("lessonId"));
    if (type.equals("EXERCISE") && lesson == null)
      throw ApiError.bad("lessonId", "Exercise requires Lesson");
    Integer seconds = time(type, b.get("timeLimitSeconds"));
    String title = Validation.text(b.get("title"), 200);
    content.context(course, lesson);
    String id = UUID.randomUUID().toString();
    db.update(
        "INSERT INTO"
            + " assessment(assessment_id,type,course_id,lesson_id,title,time_limit_seconds,ready)"
            + " VALUES(?,?,?,?,?,?,false)",
        uuid(id),
        type,
        uuid(course),
        lesson == null ? null : uuid(lesson),
        title,
        seconds);
    if (type.equals("EXERCISE"))
      db.update(
          "INSERT INTO exercise_lesson(lesson_id,assessment_id) VALUES(?,?)",
          uuid(lesson),
          uuid(id));
    return metadata(assessment(id, false));
  }

  public Map<String, Object> patch(String id, Map<String, Object> b) {
    Validation.fields(b, Set.of("title", "timeLimitSeconds", "ready"), Set.of());
    if (b.isEmpty()) throw ApiError.bad("body", "Empty patch");
    var row = assessment(id, true);
    String type = str(row.get("type"));
    String title =
        b.containsKey("title") ? Validation.text(b.get("title"), 200) : str(row.get("title"));
    Integer seconds =
        time(
            type,
            b.containsKey("timeLimitSeconds")
                ? b.get("timeLimitSeconds")
                : row.get("time_limit_seconds"));
    boolean ready =
        b.containsKey("ready") ? Validation.bool(b.get("ready")) : (Boolean) row.get("ready");
    if (ready && questions(id).isEmpty())
      throw new ApiError(409, "ASSESSMENT_NOT_READY", "At least one valid Question required");
    db.update(
        "UPDATE assessment SET title=?,time_limit_seconds=?,ready=? WHERE assessment_id=?",
        title,
        seconds,
        ready,
        uuid(id));
    return metadata(assessment(id, false));
  }

  public Map<String, Object> putQuestion(String id, String questionId, Map<String, Object> b) {
    assessment(id, true);
    String qid = questionId == null ? UUID.randomUUID().toString() : questionId;
    var q = Validation.question(b, qid);
    if (questionId == null)
      db.update(
          "INSERT INTO question(question_id,assessment_id,definition) VALUES(?,?,?)",
          uuid(qid),
          uuid(id),
          Json.text(q));
    else if (db.update(
            "UPDATE question SET definition=? WHERE question_id=? AND assessment_id=?",
            Json.text(q),
            uuid(qid),
            uuid(id))
        != 1) throw ApiError.missing();
    return q;
  }

  private boolean visible(Map<String, Object> row) {
    var c = content.context(str(row.get("course_id")), str(row.get("lesson_id")));
    return Boolean.TRUE.equals(
        c.get(row.get("lesson_id") == null ? "published" : "visibleToLearner"));
  }

  public Map<String, Object> get(String id, boolean admin) {
    var row = assessment(id, false);
    if (!admin && (!Boolean.TRUE.equals(row.get("ready")) || !visible(row)))
      throw ApiError.missing();
    var result = metadata(row);
    if (admin) result.put("questions", questions(id));
    return result;
  }

  public Map<String, Object> lessonExercise(String lesson) {
    var row =
        one(
            "SELECT a.* FROM assessment a JOIN exercise_lesson e ON e.assessment_id=a.assessment_id"
                + " WHERE e.lesson_id=?",
            uuid(lesson));
    return get(str(row.get("assessment_id")), false);
  }

  public Map<String, Object> readiness(String lesson) {
    var rows =
        db.queryForList(
            "SELECT a.* FROM assessment a JOIN exercise_lesson e ON e.assessment_id=a.assessment_id"
                + " WHERE e.lesson_id=?",
            uuid(lesson));
    if (rows.isEmpty())
      return Json.nullable(
          "lessonId", lesson, "ready", false, "exerciseId", null, "questionCount", 0);
    var a = rows.getFirst();
    int count = questions(str(a.get("assessment_id"))).size();
    return Json.nullable(
        "lessonId",
        lesson,
        "ready",
        Boolean.TRUE.equals(a.get("ready")) && count > 0,
        "exerciseId",
        str(a.get("assessment_id")),
        "questionCount",
        count);
  }

  static Map<String, Object> page(List<Map<String, Object>> items, int page, int size) {
    int from = (int) Math.min((long) page * size, items.size()),
        to = Math.min(from + size, items.size());
    return Map.of(
        "items",
        items.subList(from, to),
        "page",
        page,
        "size",
        size,
        "totalElements",
        items.size(),
        "totalPages",
        (items.size() + size - 1) / size);
  }

  public Map<String, Object> list(boolean admin, String course, String type, int page, int size) {
    if (type != null && !Validation.TYPES.contains(type))
      throw ApiError.bad("type", "Unsupported type");
    var result = new ArrayList<Map<String, Object>>();
    for (var row : db.queryForList("SELECT * FROM assessment ORDER BY assessment_id")) {
      if (course != null && !course.equals(str(row.get("course_id")))
          || type != null && !type.equals(row.get("type"))) continue;
      if (admin || Boolean.TRUE.equals(row.get("ready")) && visible(row)) result.add(metadata(row));
    }
    return page(result, page, size);
  }

  private Map<String, Object> attemptRow(String id) {
    return one("SELECT * FROM submission WHERE submission_id=? FOR UPDATE", uuid(id));
  }

  private static void owns(Map<String, Object> row, String user) {
    if (!user.equals(str(row.get("user_id"))))
      throw new ApiError(403, "FORBIDDEN", "Attempt/Result belongs to another Learner");
  }

  public Map<String, Object> start(String id, String user, String key) {
    uuid(key);
    assessment(id, true);
    // Transaction-scoped per-user lock serializes key use across different assessments as well.
    // A persistent lock row avoids PostgreSQL-only advisory locks and never calls another DB.
    db.update("INSERT INTO learner_lock(user_id) VALUES(?) ON CONFLICT DO NOTHING", uuid(user));
    one("SELECT user_id FROM learner_lock WHERE user_id=? FOR UPDATE", uuid(user));
    var existing =
        db.queryForList(
            "SELECT * FROM submission WHERE user_id=? AND idempotency_key=?",
            uuid(user),
            uuid(key));
    if (!existing.isEmpty()) {
      var old = existing.getFirst();
      if (!id.equals(str(old.get("assessment_id"))))
        throw new ApiError(409, "RESOURCE_CONFLICT", "Key already bound to another request");
      var row = attemptRow(str(old.get("submission_id")));
      expire(row);
      return attemptDto(row);
    }
    var a = assessment(id, false);
    if (!Boolean.TRUE.equals(a.get("ready")))
      throw new ApiError(409, "ASSESSMENT_NOT_READY", "Assessment is draft");
    if (!visible(a)) throw ApiError.missing();
    String level = content.level(user, str(a.get("course_id")));
    var qs = questions(id);
    if (qs.isEmpty()) throw new ApiError(409, "ASSESSMENT_NOT_READY", "No questions");
    var snap = metadata(a);
    snap.put("questions", qs);
    snap.put("levelIdAtStart", level);
    String attempt = UUID.randomUUID().toString();
    Instant now = clock.instant();
    Integer seconds = (Integer) a.get("time_limit_seconds");
    db.update(
        "INSERT INTO"
            + " submission(submission_id,assessment_id,user_id,idempotency_key,snapshot,answers,started_at,deadline_at,status)"
            + " VALUES(?,?,?,?,?,'[]',?,?,'IN_PROGRESS')",
        uuid(attempt),
        uuid(id),
        uuid(user),
        uuid(key),
        Json.text(snap),
        Timestamp.from(now),
        seconds == null ? null : Timestamp.from(now.plusSeconds(seconds)));
    return attemptDto(attemptRow(attempt));
  }

  private static Instant instant(Object value) {
    if (value == null) return null;
    if (value instanceof OffsetDateTime x) return x.toInstant();
    if (value instanceof Timestamp x) return x.toInstant();
    throw new IllegalStateException("Unsupported timestamp");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> maps(Object value) {
    return (List<Map<String, Object>>) value;
  }

  private List<Map<String, Object>> answers(Map<String, Object> row) {
    try {
      return maps(Json.M.readValue(str(row.get("answers")), List.class));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private Map<String, Object> resultFor(String attempt) {
    return Json.map(
        str(one("SELECT payload FROM result WHERE submission_id=?", uuid(attempt)).get("payload")));
  }

  private Map<String, Object> attemptDto(Map<String, Object> row) {
    var snap = Json.map(str(row.get("snapshot")));
    var qs = maps(snap.get("questions")).stream().map(Validation::publicQuestion).toList();
    Instant deadline = instant(row.get("deadline_at"));
    boolean terminal = !row.get("status").equals("IN_PROGRESS");
    return Json.nullable(
        "attemptId",
        str(row.get("submission_id")),
        "assessmentId",
        snap.get("assessmentId"),
        "courseId",
        snap.get("courseId"),
        "levelIdAtStart",
        snap.get("levelIdAtStart"),
        "status",
        row.get("status"),
        "startedAt",
        instant(row.get("started_at")).toString(),
        "deadlineAt",
        deadline == null ? null : deadline.toString(),
        "serverNow",
        clock.instant().toString(),
        "questions",
        qs,
        "answers",
        answers(row),
        "result",
        terminal ? resultFor(str(row.get("submission_id"))) : null);
  }

  private void expire(Map<String, Object> row) {
    var deadline = instant(row.get("deadline_at"));
    if (row.get("status").equals("IN_PROGRESS")
        && deadline != null
        && !clock.instant().isBefore(deadline)) finish(row, "TIMEOUT");
  }

  public Map<String, Object> attempt(String id, String user) {
    var row = attemptRow(id);
    owns(row, user);
    expire(row);
    return attemptDto(row);
  }

  public Map<String, Object> save(String id, String user, Map<String, Object> b) {
    Validation.fields(b, Set.of("answers"), Set.of("answers"));
    var source = Validation.list(b.get("answers"));
    var row = attemptRow(id);
    owns(row, user);
    expire(row);
    if (!row.get("status").equals("IN_PROGRESS")) return attemptDto(row);
    var qs = maps(Json.map(str(row.get("snapshot"))).get("questions"));
    var byId = new HashMap<String, Map<String, Object>>();
    for (var q : qs) byId.put(str(q.get("questionId")), q);
    var seen = new HashSet<String>();
    var saved = new ArrayList<Map<String, Object>>();
    for (var item : source) {
      var answer = Validation.object(item);
      String qid = Validation.uuid(answer.get("questionId"));
      var q = byId.get(qid);
      if (q == null || !seen.add(qid))
        throw ApiError.bad("answers", "Unknown or duplicate question");
      String type = str(q.get("questionType"));
      boolean single = type.equals("SINGLE_CHOICE");
      var keys =
          single
              ? Set.of("questionId", "questionType", "optionId")
              : Set.of("questionId", "questionType", "value");
      Validation.fields(answer, keys, keys);
      if (!type.equals(answer.get("questionType")))
        throw ApiError.bad("questionType", "Type mismatch");
      var out = new LinkedHashMap<>(answer);
      out.put("questionId", qid);
      if (single) {
        String option = Validation.uuid(answer.get("optionId"));
        if (maps(q.get("options")).stream().noneMatch(o -> option.equals(o.get("optionId"))))
          throw ApiError.bad("optionId", "Option outside snapshot");
        out.put("optionId", option);
      } else Validation.bool(answer.get("value"));
      saved.add(out);
    }
    String json = Json.text(saved);
    db.update("UPDATE submission SET answers=? WHERE submission_id=?", json, uuid(id));
    row.put("answers", json);
    return attemptDto(row);
  }

  public Map<String, Object> submit(String id, String user, String trace) {
    var row = attemptRow(id);
    owns(row, user);
    expire(row);
    if (row.get("status").equals("IN_PROGRESS")) finish(row, "SUBMITTED", trace);
    return resultFor(id);
  }

  private void finish(Map<String, Object> row, String status) {
    finish(row, status, UUID.randomUUID().toString());
  }

  private void finish(Map<String, Object> row, String status, String trace) {
    String id = str(row.get("submission_id"));
    var snap = Json.map(str(row.get("snapshot")));
    var saved = new HashMap<String, Map<String, Object>>();
    for (var a : answers(row)) saved.put(str(a.get("questionId")), a);
    BigDecimal earned = BigDecimal.ZERO, max = BigDecimal.ZERO;
    for (var q : maps(snap.get("questions"))) {
      var points = new BigDecimal(q.get("points").toString());
      max = max.add(points);
      var a = saved.get(str(q.get("questionId")));
      boolean correct =
          a != null
              && (q.get("questionType").equals("SINGLE_CHOICE")
                  ? Objects.equals(q.get("correctOptionId"), a.get("optionId"))
                  : Objects.equals(q.get("correctAnswer"), a.get("value")));
      if (correct) earned = earned.add(points);
    }
    if (max.signum() <= 0) throw new IllegalStateException("Invalid snapshot maxPoints");
    String resultId = UUID.randomUUID().toString(), eventId = UUID.randomUUID().toString();
    Instant now = clock.instant();
    var result =
        Json.nullable(
            "resultId",
            resultId,
            "submissionId",
            id,
            "assessmentId",
            snap.get("assessmentId"),
            "assessmentType",
            snap.get("type"),
            "courseId",
            snap.get("courseId"),
            "levelIdAtStart",
            snap.get("levelIdAtStart"),
            "lessonId",
            snap.get("lessonId"),
            "status",
            status,
            "earnedPoints",
            earned,
            "maxPoints",
            max,
            "scorePercent",
            earned.multiply(BigDecimal.valueOf(100)).divide(max, 2, RoundingMode.HALF_UP),
            "completedAt",
            now.toString(),
            "progressSync",
            "EVENTUAL");
    var payload = new LinkedHashMap<>(result);
    payload.remove("status");
    payload.remove("scorePercent");
    payload.remove("progressSync");
    payload.put("userId", str(row.get("user_id")));
    var event =
        Json.nullable(
            "eventId",
            eventId,
            "occurredAt",
            now.toString(),
            "correlationId",
            Input.id(trace).toString(),
            "schemaVersion",
            1,
            "eventType",
            "AssessmentCompleted",
            "payload",
            payload);
    db.update(
        "INSERT INTO result(result_id,submission_id,user_id,completed_at,payload)"
            + " VALUES(?,?,?,?,?)",
        uuid(resultId),
        uuid(id),
        row.get("user_id"),
        Timestamp.from(now),
        Json.text(result));
    db.update(
        "INSERT INTO"
            + " outbox_event(event_id,submission_id,event_type,schema_version,topic,partition_key,payload,occurred_at,status)"
            + " VALUES(?,?,'AssessmentCompleted',1,'assessment.completed.v1',?,?,?,'PENDING')",
        uuid(eventId),
        uuid(id),
        str(row.get("user_id")),
        Json.text(event),
        Timestamp.from(now));
    db.update("UPDATE submission SET status=? WHERE submission_id=?", status, uuid(id));
    row.put("status", status);
  }

  public Map<String, Object> result(String id, String user) {
    var row = one("SELECT * FROM result WHERE result_id=?", uuid(id));
    owns(row, user);
    return Json.map(str(row.get("payload")));
  }

  public Map<String, Object> attempts(
      String user, String assessmentId, String status, int page, int size) {
    if (status != null && !Set.of("IN_PROGRESS", "SUBMITTED", "TIMEOUT").contains(status))
      throw ApiError.bad("status", "Unsupported status");
    var items = new ArrayList<Map<String, Object>>();
    for (var entry :
        db.queryForList(
            "SELECT submission_id FROM submission WHERE user_id=? ORDER BY started_at"
                + " DESC,submission_id",
            uuid(user))) {
      var row = attemptRow(str(entry.get("submission_id")));
      expire(row);
      if (assessmentId != null && !assessmentId.equals(str(row.get("assessment_id")))
          || status != null && !status.equals(row.get("status"))) continue;
      items.add(attemptDto(row));
    }
    return page(items, page, size);
  }

  public Map<String, Object> results(
      String user, String course, String assessmentId, String type, int page, int size) {
    if (type != null && !Validation.TYPES.contains(type))
      throw ApiError.bad("type", "Unsupported type");
    var items = new ArrayList<Map<String, Object>>();
    for (var row :
        db.queryForList(
            "SELECT payload FROM result WHERE user_id=? ORDER BY completed_at DESC,result_id",
            uuid(user))) {
      var value = Json.map(str(row.get("payload")));
      if (course != null && !course.equals(value.get("courseId"))
          || assessmentId != null && !assessmentId.equals(value.get("assessmentId"))
          || type != null && !type.equals(value.get("assessmentType"))) continue;
      items.add(value);
    }
    return page(items, page, size);
  }

  public void expireDue() {
    for (var entry :
        db.queryForList(
            "SELECT submission_id FROM submission WHERE status='IN_PROGRESS' AND deadline_at<=?"
                + " ORDER BY submission_id",
            Timestamp.from(clock.instant()))) expire(attemptRow(str(entry.get("submission_id"))));
  }
}
