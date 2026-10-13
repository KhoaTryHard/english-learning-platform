package vn.group7.content;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.group7.core.*;

@Component
public class ContentStore {
  record Resource(
      String table, String id, String create, String patch, Map<String, String> fields) {}

  static Map<String, String> fields(String... x) {
    var m = new LinkedHashMap<String, String>();
    for (int i = 0; i < x.length; i += 2) m.put(x[i], x[i + 1]);
    return m;
  }

  static final Map<String, Resource> R =
      Map.of(
          "courses",
              new Resource(
                  "course",
                  "course_id",
                  "CourseCreate",
                  "CoursePatch",
                  fields("courseId", "course_id", "name", "name", "published", "published")),
          "modules",
              new Resource(
                  "course_module",
                  "module_id",
                  "ModuleCreate",
                  "ModulePatch",
                  fields(
                      "moduleId",
                      "module_id",
                      "courseId",
                      "course_id",
                      "title",
                      "title",
                      "orderNo",
                      "order_no",
                      "published",
                      "published")),
          "lessons",
              new Resource(
                  "lesson",
                  "lesson_id",
                  "LessonCreate",
                  "LessonPatch",
                  fields(
                      "lessonId",
                      "lesson_id",
                      "moduleId",
                      "module_id",
                      "title",
                      "title",
                      "content",
                      "content",
                      "orderNo",
                      "order_no",
                      "published",
                      "published")),
          "vocabulary",
              new Resource(
                  "vocabulary",
                  "vocabulary_id",
                  null,
                  "VocabularyPatch",
                  fields(
                      "vocabularyId",
                      "vocabulary_id",
                      "lessonId",
                      "lesson_id",
                      "word",
                      "word",
                      "meaning",
                      "meaning",
                      "example",
                      "example",
                      "phonetic",
                      "phonetic",
                      "published",
                      "published")),
          "learning-contents",
              new Resource(
                  "learning_content",
                  "content_id",
                  "LearningContentCreate",
                  "LearningContentPatch",
                  fields(
                      "contentId",
                      "content_id",
                      "lessonId",
                      "lesson_id",
                      "type",
                      "type",
                      "title",
                      "title",
                      "text",
                      "text",
                      "resourceUrl",
                      "resource_url",
                      "published",
                      "published")));
  private final Db db;
  private final OwnerClient client;
  private final Media media;
  private final String assessment;

  public ContentStore(Db d, OwnerClient c, Media m, @Value("${platform.assessment-url}") String a) {
    db = d;
    client = c;
    media = m;
    assessment = a;
  }

  public Map<String, Object> row(String resource, UUID id) {
    var r = R.get(resource);
    return db.one("SELECT * FROM " + r.table + " WHERE " + r.id + "=?", id);
  }

  public UUID courseOfLesson(UUID id) {
    return (UUID)
        db.one(
                "SELECT m.course_id FROM lesson l JOIN course_module m USING(module_id) WHERE"
                    + " l.lesson_id=?",
                id)
            .get("course_id");
  }

  public boolean lessonVisible(UUID id) {
    return (boolean)
        db.one(
                "SELECT l.published AND m.published AND c.published AS visible FROM lesson l JOIN"
                    + " course_module m USING(module_id) JOIN course c USING(course_id) WHERE"
                    + " l.lesson_id=?",
                id)
            .get("visible");
  }

  public boolean visible(String resource, Map<String, Object> row) {
    if (!Boolean.TRUE.equals(row.get("published"))) return false;
    return switch (resource) {
      case "courses" -> true;
      case "modules" ->
          Boolean.TRUE.equals(this.row("courses", (UUID) row.get("course_id")).get("published"));
      case "lessons" -> lessonVisible((UUID) row.get("lesson_id"));
      default -> lessonVisible((UUID) row.get("lesson_id"));
    };
  }

  public void visibleLesson(UUID id) {
    if (!lessonVisible(id)) throw new ApiError(404, "CONTENT_UNAVAILABLE", "Nội dung tạm ẩn");
  }

  public Map<String, Object> dto(String resource, Map<String, Object> row) {
    var r = R.get(resource);
    var m = new LinkedHashMap<String, Object>();
    r.fields.forEach((k, v) -> m.put(k, row.get(v)));
    if (resource.equals("lessons")) m.put("courseId", courseOfLesson((UUID) row.get("lesson_id")));
    if (resource.equals("vocabulary"))
      m.put("courseId", courseOfLesson((UUID) row.get("lesson_id")));
    if (resource.equals("learning-contents"))
      m.put(
          "availability",
          !"LISTENING".equals(row.get("type")) || media.available((String) row.get("resource_url"))
              ? "AVAILABLE"
              : "UNAVAILABLE");
    return m;
  }

  public Map<String, Object> get(String resource, UUID id, boolean learner) {
    var row = row(resource, id);
    if (learner && !visible(resource, row))
      throw new ApiError(404, "CONTENT_UNAVAILABLE", "Nội dung tạm ẩn");
    return dto(resource, row);
  }

  public Map<String, Object> list(
      String resource,
      String parent,
      UUID parentId,
      String type,
      int page,
      int size,
      boolean learner) {
    var r = R.get(resource);
    var where = new ArrayList<String>();
    var args = new ArrayList<Object>();
    if (parent != null && parentId != null) {
      where.add("t." + parent + "=?");
      args.add(parentId);
    }
    if (type != null) {
      if (!Set.of("GRAMMAR", "LISTENING", "READING").contains(type))
        throw ApiError.bad("type", "Type không hợp lệ");
      where.add("t.type=?");
      args.add(type);
    }
    String from = r.table + " t";
    if (learner) {
      where.add("t.published");
      if (resource.equals("modules")) {
        from += " JOIN course c ON c.course_id=t.course_id";
        where.add("c.published");
      }
      if (resource.equals("lessons")) {
        from +=
            " JOIN course_module m ON m.module_id=t.module_id JOIN course c ON"
                + " c.course_id=m.course_id";
        where.add("m.published AND c.published");
      }
    }
    String w = where.isEmpty() ? "" : " WHERE " + String.join(" AND ", where);
    long total = db.count("SELECT count(*) FROM " + from + w, args.toArray());
    args.add(size);
    args.add((long) page * size);
    String order =
        Set.of("modules", "lessons").contains(resource) ? "t.order_no,t." + r.id : "t." + r.id;
    return Map.of(
        "items",
        db
            .rows(
                "SELECT t.* FROM " + from + w + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                args.toArray())
            .stream()
            .map(x -> dto(resource, x))
            .toList(),
        "page",
        page,
        "size",
        size,
        "totalElements",
        total,
        "totalPages",
        (total + size - 1) / size);
  }

  private void clean(Map<String, Object> cols, String resource) {
    for (var e : new ArrayList<>(cols.entrySet())) {
      String key = e.getKey();
      Object val = e.getValue();
      if (key.endsWith("_id")) {
        if (val instanceof String s) cols.put(key, Input.id(s));
      } else if (Set.of(
              "name", "title", "content", "word", "meaning", "text", "example", "phonetic")
          .contains(key)) {
        int max =
            switch (key) {
              case "word", "phonetic" -> 128;
              case "meaning" -> 500;
              case "example" -> 1000;
              case "content", "text" -> 20000;
              default -> 200;
            };
        cols.put(key, Input.text(val, max, Set.of("example", "phonetic", "text").contains(key)));
      }
    }
    if (cols.containsKey("name"))
      cols.put("name_normalized", Input.norm((String) cols.get("name")));
    if (cols.containsKey("title") && !resource.equals("learning-contents"))
      cols.put("title_normalized", Input.norm((String) cols.get("title")));
    if (cols.containsKey("word"))
      cols.put("word_normalized", Input.norm((String) cols.get("word")));
    if (cols.containsKey("meaning"))
      cols.put("meaning_normalized", Input.norm((String) cols.get("meaning")));
  }

  private void contentValid(Map<String, Object> merged) {
    String type = (String) merged.get("type");
    if (Set.of("GRAMMAR", "READING").contains(type) && merged.get("text") == null)
      throw ApiError.bad("text", "Grammar/Reading cần text");
    if ("LISTENING".equals(type)) {
      if (!(merged.get("resource_url") instanceof String))
        throw ApiError.bad("resourceUrl", "Listening cần URL");
      media.allowed((String) merged.get("resource_url"));
    }
  }

  public Map<String, Object> create(String resource, Map<String, Object> b) {
    var r = R.get(resource);
    Input.check(r.create, b);
    var cols = new LinkedHashMap<String, Object>();
    b.forEach((k, v) -> cols.put(r.fields.get(k), v));
    clean(cols, resource);
    if (resource.equals("modules")) row("courses", (UUID) cols.get("course_id"));
    if (resource.equals("lessons")) row("modules", (UUID) cols.get("module_id"));
    if (resource.equals("learning-contents")) row("lessons", (UUID) cols.get("lesson_id"));
    if (resource.equals("learning-contents")) contentValid(cols);
    UUID id = UUID.randomUUID();
    cols.put(r.id, id);
    insert(r.table, cols);
    return get(resource, id, false);
  }

  void insert(String table, Map<String, Object> cols) {
    db.run(
        "INSERT INTO "
            + table
            + "("
            + String.join(",", cols.keySet())
            + ") VALUES("
            + String.join(",", Collections.nCopies(cols.size(), "?"))
            + ")",
        cols.values().toArray());
  }

  public Map<String, Object> patch(String resource, UUID id, Map<String, Object> b) {
    var r = R.get(resource);
    Input.check(r.patch, b);
    return db.tx(
        () -> {
          var old = db.one("SELECT * FROM " + r.table + " WHERE " + r.id + "=? FOR UPDATE", id);
          var cols = new LinkedHashMap<String, Object>();
          b.forEach((k, v) -> cols.put(r.fields.get(k), v));
          clean(cols, resource);
          var merged = new LinkedHashMap<>(old);
          merged.putAll(cols);
          if (resource.equals("learning-contents")) contentValid(merged);
          if (resource.equals("lessons") && Boolean.TRUE.equals(merged.get("published")))
            readiness(id);
          var args = new ArrayList<>(cols.values());
          args.add(id);
          db.run(
              "UPDATE "
                  + r.table
                  + " SET "
                  + String.join(",", cols.keySet().stream().map(k -> k + "=?").toList())
                  + " WHERE "
                  + r.id
                  + "=?",
              args.toArray());
          return get(resource, id, false);
        });
  }

  public Map<String, Object> readiness(UUID id) {
    Map<String, Object> r;
    try {
      r =
          client.get(
              assessment, "/internal/v1/lessons/" + id + "/exercise-readiness", "assessment");
    } catch (ApiError e) {
      if (e.status == 404) throw ApiError.dependency();
      throw e;
    }
    if (!id.toString().equals(r.get("lessonId"))
        || !(r.get("ready") instanceof Boolean)
        || !(r.get("questionCount") instanceof Number)) throw ApiError.dependency();
    if (!Boolean.TRUE.equals(r.get("ready")))
      throw new ApiError(409, "ASSESSMENT_NOT_READY", "Exercise chưa sẵn sàng");
    if (((Number) r.get("questionCount")).intValue() < 1
        || !(r.get("exerciseId") instanceof String s)) throw ApiError.dependency();
    try {
      Input.id(s);
    } catch (ApiError e) {
      throw ApiError.dependency();
    }
    return r;
  }

  public Map<String, Object> learning(UUID id) {
    visibleLesson(id);
    var ready = readiness(id);
    return Map.of(
        "lesson",
        get("lessons", id, true),
        "vocabulary",
        db
            .rows(
                "SELECT * FROM vocabulary WHERE lesson_id=? AND published ORDER BY vocabulary_id",
                id)
            .stream()
            .map(x -> dto("vocabulary", x))
            .toList(),
        "contents",
        db
            .rows(
                "SELECT * FROM learning_content WHERE lesson_id=? AND published ORDER BY"
                    + " content_id",
                id)
            .stream()
            .map(x -> dto("learning-contents", x))
            .toList(),
        "exerciseId",
        ready.get("exerciseId"));
  }

  public Map<String, Object> courseContext(UUID id) {
    var c = row("courses", id);
    return Map.of(
        "courseId",
        id,
        "published",
        c.get("published"),
        "totalLessons",
        db.count(
            "SELECT count(*) FROM lesson l JOIN course_module m USING(module_id) WHERE"
                + " m.course_id=?",
            id),
        "levels",
        levels(id),
        "generatedAt",
        Instant.now().toString());
  }

  public List<Map<String, Object>> levels(UUID course) {
    return db.rows("SELECT * FROM level WHERE course_id=? ORDER BY level_id", course).stream()
        .map(
            x ->
                Json.nullable(
                    "levelId",
                    x.get("level_id"),
                    "courseId",
                    x.get("course_id"),
                    "name",
                    x.get("name")))
        .toList();
  }

  public Map<String, Object> lessonContext(UUID id) {
    var l = row("lessons", id);
    return Map.of(
        "lessonId",
        id,
        "moduleId",
        l.get("module_id"),
        "courseId",
        courseOfLesson(id),
        "visibleToLearner",
        lessonVisible(id));
  }

  public Map<String, Object> saved(Map<String, Object> relation) {
    var rows =
        db.rows("SELECT * FROM vocabulary WHERE vocabulary_id=?", relation.get("vocabulary_id"));
    Map<String, Object> source = null;
    if (!rows.isEmpty()) {
      var v = rows.getFirst();
      source =
          Json.nullable(
              "word",
              v.get("word"),
              "meaning",
              v.get("meaning"),
              "example",
              v.get("example"),
              "phonetic",
              v.get("phonetic"));
    }
    return Json.nullable(
        "vocabularyId",
        relation.get("vocabulary_id"),
        "addedAt",
        Db.instant(relation.get("added_at")),
        "availability",
        source == null ? "UNAVAILABLE" : "AVAILABLE",
        "content",
        source);
  }

  public Map<String, Object> save(UUID user, UUID vocabulary) {
    var existing =
        db.rows(
            "SELECT * FROM user_vocabulary WHERE user_id=? AND vocabulary_id=?", user, vocabulary);
    if (!existing.isEmpty()) return saved(existing.getFirst());
    get("vocabulary", vocabulary, true);
    db.run(
        "INSERT INTO user_vocabulary(user_id,vocabulary_id) VALUES(?,?) ON"
            + " CONFLICT(user_id,vocabulary_id) DO NOTHING",
        user,
        vocabulary);
    return saved(
        db.one(
            "SELECT * FROM user_vocabulary WHERE user_id=? AND vocabulary_id=?", user, vocabulary));
  }

  public void remove(UUID user, UUID vocabulary) {
    db.run("DELETE FROM user_vocabulary WHERE user_id=? AND vocabulary_id=?", user, vocabulary);
  }

  public Map<String, Object> savedList(UUID user, int page, int size) {
    long n = db.count("SELECT count(*) FROM user_vocabulary WHERE user_id=?", user);
    return Map.of(
        "items",
        db
            .rows(
                "SELECT * FROM user_vocabulary WHERE user_id=? ORDER BY user_vocabulary_id LIMIT ?"
                    + " OFFSET ?",
                user,
                size,
                (long) page * size)
            .stream()
            .map(this::saved)
            .toList(),
        "page",
        page,
        "size",
        size,
        "totalElements",
        n,
        "totalPages",
        (n + size - 1) / size);
  }
}
