package vn.group7.content;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.group7.core.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ContentIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> PG =
      new PostgreSQLContainer<>("postgres:17").withInitScript("test-roles.sql");

  static final WireMockServer MOCK = mock();

  static WireMockServer mock() {
    var w = new WireMockServer(0);
    w.start();
    return w;
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", PG::getJdbcUrl);
    r.add("spring.datasource.username", PG::getUsername);
    r.add("spring.datasource.password", PG::getPassword);
    r.add("spring.flyway.enabled", () -> true);
    r.add("platform.keys", () -> TestSupport.KEYS.toString());
    r.add("platform.assessment-url", MOCK::baseUrl);
  }

  @Autowired Db db;
  @org.springframework.boot.test.web.server.LocalServerPort int port;
  @Autowired ContentStore store;
  @Autowired Imports imports;
  UUID course, module, lesson, user;

  @BeforeEach
  void data() {
    db.run("DELETE FROM user_vocabulary");
    db.run("DELETE FROM vocabulary");
    db.run("DELETE FROM learning_content");
    db.run("DELETE FROM lesson");
    db.run("DELETE FROM course_module");
    db.run("DELETE FROM level");
    db.run("DELETE FROM course");
    course = (UUID) store.create("courses", Map.of("name", "English")).get("courseId");
    module =
        (UUID)
            store
                .create(
                    "modules",
                    Map.of("courseId", course.toString(), "title", "Greetings", "orderNo", 1))
                .get("moduleId");
    lesson =
        (UUID)
            store
                .create(
                    "lessons",
                    Map.of(
                        "moduleId",
                        module.toString(),
                        "title",
                        "Lesson",
                        "content",
                        "Body",
                        "orderNo",
                        1))
                .get("lessonId");
    user = UUID.randomUUID();
    MOCK.resetAll();
  }

  void readiness(boolean ready) {
    MOCK.stubFor(
        get(urlEqualTo("/internal/v1/lessons/" + lesson + "/exercise-readiness"))
            .willReturn(
                okJson(
                    Json.text(
                        Json.nullable(
                            "lessonId",
                            lesson.toString(),
                            "ready",
                            ready,
                            "exerciseId",
                            ready ? UUID.randomUUID().toString() : null,
                            "questionCount",
                            ready ? 1 : 0)))));
  }

  void publish() {
    readiness(true);
    store.patch("courses", course, Map.of("published", true));
    store.patch("modules", module, Map.of("published", true));
    store.patch("lessons", lesson, Map.of("published", true));
  }

  MockMultipartFile fixture(String name) throws Exception {
    String ext = name.endsWith("xlsx") ? "xlsx" : "docx";
    String mime =
        ext.equals("xlsx")
            ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    try (var stream = getClass().getResourceAsStream("/fixtures/" + name)) {
      if (stream == null) throw new IllegalStateException("Fixture not packaged: " + name);
      return new MockMultipartFile("file", name, mime, stream.readAllBytes());
    }
  }

  @Test
  void macroContainersLeaveNoBusinessRows() throws Exception {
    long vocabularyBefore = db.count("SELECT count(*) FROM vocabulary");
    long lessonsBefore = db.count("SELECT count(*) FROM lesson");
    for (String type : List.of("vocabulary", "lessons")) {
      String name = type.equals("vocabulary") ? "vocabulary-valid.xlsx" : "lesson-valid.docx";
      var file = ImportContainerTest.upload(name, true);
      var error = assertThrows(ApiError.class,
          () -> imports.run(type, file, type.equals("vocabulary") ? lesson : module, 2));
      assertEquals(415, error.status);
      assertEquals(0, ((Map<?, ?>) error.extra.get("import")).get("createdCount"));
    }
    assertEquals(vocabularyBefore, db.count("SELECT count(*) FROM vocabulary"));
    assertEquals(lessonsBefore, db.count("SELECT count(*) FROM lesson"));
  }

  @Test
  void atomicExcelAndWordRules() throws Exception {
    var result = imports.run("vocabulary", fixture("vocabulary-valid.xlsx"), lesson, null);
    assertEquals(2, result.get("createdCount"));
    assertEquals(2, db.count("SELECT count(*) FROM vocabulary"));
    ApiError duplicate =
        assertThrows(
            ApiError.class,
            () -> imports.run("vocabulary", fixture("vocabulary-valid.xlsx"), lesson, null));
    assertEquals(409, duplicate.status);
    assertEquals(0, ((Map<?, ?>) duplicate.extra.get("import")).get("createdCount"));
    db.run("DELETE FROM vocabulary");
    assertEquals(
        422,
        assertThrows(
                ApiError.class,
                () -> imports.run("vocabulary", fixture("vocabulary-invalid.xlsx"), lesson, null))
            .status);
    assertEquals(0, db.count("SELECT count(*) FROM vocabulary"));
    assertEquals(
        1,
        imports
            .run("lessons", fixture("lesson-leading-empty.docx"), module, 2)
            .get("createdCount"));
    assertEquals(
        422,
        assertThrows(
                ApiError.class,
                () -> imports.run("lessons", fixture("lesson-missing-title.docx"), module, 3))
            .status);
    assertEquals(2, db.count("SELECT count(*) FROM lesson"));
    assertEquals(
        409,
        assertThrows(
                ApiError.class,
                () -> imports.run("lessons", fixture("lesson-valid.docx"), module, 2))
            .status);
  }

  @Test
  void savedReferenceSurvivesHiddenParentAndSourceUpdates() throws Exception {
    publish();
    imports.run("vocabulary", fixture("vocabulary-valid.xlsx"), lesson, null);
    UUID v =
        (UUID)
            db.one("SELECT vocabulary_id FROM vocabulary WHERE word='hello'").get("vocabulary_id");
    store.patch("vocabulary", v, Map.of("published", true));
    store.save(user, v);
    store.patch("courses", course, Map.of("published", false));
    assertEquals("AVAILABLE", store.save(user, v).get("availability"));
    assertEquals(1, db.count("SELECT count(*) FROM user_vocabulary"));
    store.patch("vocabulary", v, Map.of("meaning", "chào bạn"));
    var item = (Map<?, ?>) ((List<?>) store.savedList(user, 0, 20).get("items")).getFirst();
    assertEquals("chào bạn", ((Map<?, ?>) item.get("content")).get("meaning"));
    assertEquals(404, assertThrows(ApiError.class, () -> store.save(UUID.randomUUID(), v)).status);
    store.remove(user, v);
    store.remove(user, v);
    assertEquals(2, db.count("SELECT count(*) FROM vocabulary"));
  }

  @Test
  void readinessHierarchyConstraintsAndDenominator() {
    readiness(false);
    assertEquals(
        409,
        assertThrows(
                ApiError.class, () -> store.patch("lessons", lesson, Map.of("published", true)))
            .status);
    MOCK.resetAll();
    assertEquals(
        503,
        assertThrows(
                ApiError.class, () -> store.patch("lessons", lesson, Map.of("published", true)))
            .status);
    assertEquals(1L, store.courseContext(course).get("totalLessons"));
    assertEquals(
        409,
        assertThrows(
                ApiError.class,
                () ->
                    store.create(
                        "modules",
                        Map.of("courseId", course.toString(), "title", "Other", "orderNo", 1)))
            .status);
    assertEquals(
        400,
        assertThrows(
                ApiError.class,
                () ->
                    store.patch(
                        "lessons", lesson, Map.of("moduleId", UUID.randomUUID().toString())))
            .status);
    assertThrows(
        ApiError.class,
        () ->
            db.run(
                "INSERT INTO learning_content(lesson_id,type,title) VALUES(?,'GRAMMAR','Empty')",
                lesson));
    assertThrows(
        ApiError.class,
        () ->
            db.run(
                "INSERT INTO vocabulary(lesson_id,word,meaning,word_normalized,meaning_normalized)"
                    + " VALUES(?,'','x','','x')",
                lesson));
  }

  @Test
  void concurrentSaveAndImportRollback() throws Exception {
    publish();
    imports.run("vocabulary", fixture("vocabulary-valid.xlsx"), lesson, null);
    UUID v = (UUID) db.one("SELECT vocabulary_id FROM vocabulary LIMIT 1").get("vocabulary_id");
    store.patch("vocabulary", v, Map.of("published", true));
    try (var pool = Executors.newFixedThreadPool(4)) {
      var jobs = new ArrayList<Future<?>>();
      for (int i = 0; i < 8; i++) jobs.add(pool.submit(() -> store.save(user, v)));
      for (var j : jobs) j.get();
    }
    assertEquals(1, db.count("SELECT count(*) FROM user_vocabulary"));
    db.run("DELETE FROM user_vocabulary");
    db.run("DELETE FROM vocabulary");
    try (var pool = Executors.newFixedThreadPool(2)) {
      var file = fixture("vocabulary-valid.xlsx");
      var a =
          pool.submit(
              () -> {
                try {
                  imports.run("vocabulary", file, lesson, null);
                  return 201;
                } catch (ApiError e) {
                  return e.status;
                }
              });
      var b =
          pool.submit(
              () -> {
                try {
                  imports.run("vocabulary", file, lesson, null);
                  return 201;
                } catch (ApiError e) {
                  return e.status;
                }
              });
      var statuses = List.of(a.get(), b.get());
      assertTrue(statuses.contains(201));
      assertTrue(statuses.contains(409));
    }
    assertEquals(2, db.count("SELECT count(*) FROM vocabulary"));
  }

  @Test
  void runtimePrivilegesAndForeignKeys() throws Exception {
    try (var c = DriverManager.getConnection(PG.getJdbcUrl(), "content_runtime", "test-runtime");
        var s = c.createStatement()) {
      assertThrows(SQLException.class, () -> s.execute("DELETE FROM course"));
      assertThrows(SQLException.class, () -> s.execute("TRUNCATE lesson"));
      assertThrows(SQLException.class, () -> s.execute("CREATE TABLE forbidden(x int)"));
    }
    assertThrows(
        ApiError.class,
        () ->
            db.run(
                "INSERT INTO user_vocabulary(user_id,vocabulary_id) VALUES(?,?)",
                user,
                UUID.randomUUID()));
  }

  @AfterAll
  static void stop() {
    MOCK.stop();
  }

  @Test
  void contextPagingIncludesDraftCoursesAndHiddenLessonDenominator() throws Exception {
    store.create("courses", Map.of("name", "Second course"));
    var seen = new HashSet<String>();
    long totalLessons = 0;
    var http = java.net.http.HttpClient.newHttpClient();
    for (int page = 0; page < 2; page++) {
      var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:"
          + port + "/internal/v1/courses/contexts?size=1&page=" + page))
          .header("Authorization", "Bearer " + TestSupport.caller("progress").service("content"))
          .GET().build();
      var response = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
      assertEquals(200, response.statusCode(), response.body());
      var body = Json.map(response.body());
      assertEquals(2, ((Number) body.get("totalElements")).intValue());
      var items = (List<?>) body.get("items");
      assertEquals(1, items.size());
      var item = (Map<?, ?>) items.getFirst();
      assertTrue(seen.add(item.get("courseId").toString()));
      assertEquals(false, item.get("published"));
      totalLessons += ((Number) item.get("totalLessons")).longValue();
    }
    assertEquals(1, totalLessons);
  }

  @Test
  void privateContextApisAndCallerAllowlist() throws Exception {
    var http = java.net.http.HttpClient.newHttpClient();
    var request =
        java.net.http.HttpRequest.newBuilder(
                java.net.URI.create(
                    "http://localhost:" + port + "/internal/v1/courses/" + course + "/context"))
            .header("Authorization", "Bearer " + TestSupport.caller("user").service("content"))
            .GET()
            .build();
    var response = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    assertEquals(1, ((Number) Json.map(response.body()).get("totalLessons")).intValue());
    request =
        java.net.http.HttpRequest.newBuilder(
                java.net.URI.create(
                    "http://localhost:" + port + "/internal/v1/lessons/" + lesson + "/context"))
            .header(
                "Authorization", "Bearer " + TestSupport.caller("assessment").service("content"))
            .GET()
            .build();
    response = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    assertEquals(false, Json.map(response.body()).get("visibleToLearner"));
    request =
        java.net.http.HttpRequest.newBuilder(
                java.net.URI.create("http://localhost:" + port + "/internal/v1/courses/contexts"))
            .header("Authorization", "Bearer " + TestSupport.caller("progress").service("content"))
            .GET()
            .build();
    assertEquals(
        200, http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
    request =
        java.net.http.HttpRequest.newBuilder(
                java.net.URI.create("http://localhost:" + port + "/internal/v1/courses/contexts"))
            .header("Authorization", "Bearer " + TestSupport.caller("api").service("content"))
            .GET()
            .build();
    assertEquals(
        403, http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
  }
}
