package vn.group7.user;

import static org.junit.jupiter.api.Assertions.*;

import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.*;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import vn.group7.core.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class UserIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> PG =
      new PostgreSQLContainer<>("postgres:17").withInitScript("test-roles.sql");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", PG::getJdbcUrl);
    r.add("spring.datasource.username", PG::getUsername);
    r.add("spring.datasource.password", PG::getPassword);
    r.add("spring.flyway.enabled", () -> true);
    r.add("platform.keys", () -> TestSupport.KEYS.toString());
  }

  @LocalServerPort int port;
  @Autowired Db db;
  HttpClient http;
  String csrf;

  @BeforeEach
  void clear() {
    db.run("DELETE FROM spring_session_attributes");
    db.run("DELETE FROM spring_session");
    db.run("DELETE FROM revoked_token");
    db.run("DELETE FROM learning_setup");
    db.run("DELETE FROM learner_profile");
    db.run("DELETE FROM app_user");
    http =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
            .build();
    csrf = null;
  }

  HttpResponse<String> call(String method, String path, Map<String, Object> body, String extra)
      throws Exception {
    var b =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer " + TestSupport.caller("api").service("user"))
            .header("Origin", "http://localhost:5173")
            .header("Content-Type", "application/json");
    if (csrf != null) b.header("X-CSRF-Token", csrf);
    if (extra != null) b.header("X-Gateway-Principal", extra);
    b.method(
        method,
        body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(Json.text(body)));
    return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
  }

  void csrf() throws Exception {
    var r = call("GET", "/api/v1/auth/csrf", null, null);
    assertEquals(200, r.statusCode(), r.body());
    csrf = Json.map(r.body()).get("csrfToken").toString();
  }

  Map<String, Object> registration(String email) throws Exception {
    var r =
        call(
            "POST",
            "/api/v1/auth/register",
            Map.of("email", email, "password", "long-password-12345"),
            null);
    assertEquals(201, r.statusCode(), r.body());
    return Json.map(r.body());
  }

  @Test
  void registrationNormalizationAndNoEscalation() throws Exception {
    csrf();
    registration("Khoa@example.com");
    assertEquals(
        409,
        call(
                "POST",
                "/api/v1/auth/register",
                Map.of("email", " khoa@example.com ", "password", "long-password-12345"),
                null)
            .statusCode());
    assertEquals(
        400,
        call(
                "POST",
                "/api/v1/auth/register",
                Map.of(
                    "email",
                    "admin@example.com",
                    "password",
                    "long-password-12345",
                    "role",
                    "ADMIN"),
                null)
            .statusCode());
    assertEquals(
        400,
        call(
                "POST",
                "/api/v1/auth/register",
                Map.of("email", "unicode@example.com", "password", "á".repeat(37)),
                null)
            .statusCode());
    assertEquals(1, db.count("SELECT count(*) FROM app_user"));
    assertTrue(
        db.one("SELECT password_hash FROM app_user")
            .get("password_hash")
            .toString()
            .startsWith("$2"));
  }

  @Test
  void csrfRotationLogoutReplayAndProfile() throws Exception {
    assertEquals(
        403,
        call(
                "POST",
                "/api/v1/auth/register",
                Map.of("email", "khoa@example.com", "password", "long-password-12345"),
                null)
            .statusCode());
    csrf();
    var u = registration("khoa@example.com");
    String old = csrf;
    var login =
        call(
            "POST",
            "/api/v1/auth/login",
            Map.of("email", "khoa@example.com", "password", "long-password-12345"),
            null);
    assertEquals(200, login.statusCode(), login.body());
    String token =
        login.headers().allValues("set-cookie").stream()
            .filter(s -> s.startsWith("access_token="))
            .findFirst()
            .orElseThrow()
            .split(";")[0]
            .substring("access_token=".length());
    csrf();
    assertNotEquals(old, csrf);
    UUID uid = UUID.fromString(u.get("userId").toString());
    String path = "/api/v1/me/profile";
    assertEquals(
        200,
        call(
                "PATCH",
                path,
                Json.nullable("fullName", null),
                TestSupport.assertion("user", "PATCH", path, uid, "LEARNER"))
            .statusCode());
    assertEquals(
        204,
        call(
                "POST",
                "/api/v1/auth/logout",
                null,
                TestSupport.assertion("user", "POST", "/api/v1/auth/logout", uid, "LEARNER"))
            .statusCode());
    assertEquals(
        401,
        call(
                "POST",
                "/internal/v1/sessions/validate",
                Map.of("token", token, "requestMethod", "GET"),
                null)
            .statusCode());
    assertEquals(1, db.count("SELECT count(*) FROM revoked_token"));
  }

  @Test
  void blockedExpiredForgedAndOldContractFailClosed() throws Exception {
    csrf();
    var u = registration("khoa@example.com");
    UUID id = Input.id(u.get("userId").toString());
    String token =
        TestSupport.caller("user")
            .sign(
                "user",
                "api",
                Map.of("sub", id.toString(), "role", "LEARNER"),
                Instant.now().plusSeconds(60));
    assertEquals(
        400,
        call("POST", "/internal/v1/sessions/validate", Map.of("token", token), null).statusCode());
    assertEquals(
        403,
        call(
                "POST",
                "/internal/v1/sessions/validate",
                Map.of("token", token, "requestMethod", "POST"),
                null)
            .statusCode());
    db.run("UPDATE app_user SET status='BLOCKED' WHERE user_id=?", id);
    assertEquals(
        401,
        call(
                "POST",
                "/internal/v1/sessions/validate",
                Map.of("token", token, "requestMethod", "GET"),
                null)
            .statusCode());
    String expired =
        TestSupport.caller("user")
            .sign(
                "user",
                "api",
                Map.of("sub", id.toString(), "role", "LEARNER"),
                Instant.now().minusSeconds(1));
    assertEquals(
        401,
        call(
                "POST",
                "/internal/v1/sessions/validate",
                Map.of("token", expired, "requestMethod", "GET"),
                null)
            .statusCode());
    assertEquals(401, call("GET", "/api/v1/me/profile", null, "forged").statusCode());
  }

  @Test
  void runtimeLeastPrivilegeAndExternalSetup() throws Exception {
    try (var c = DriverManager.getConnection(PG.getJdbcUrl(), "user_runtime", "test-runtime")) {
      try (var s = c.createStatement()) {
        s.execute(
            "INSERT INTO app_user(email_normalized,password_hash,role,status)"
                + " VALUES('runtime@example.com','hash','LEARNER','ACTIVE')");
        assertThrows(SQLException.class, () -> s.execute("TRUNCATE app_user"));
        assertThrows(SQLException.class, () -> s.execute("CREATE TABLE forbidden(x int)"));
        assertThrows(SQLException.class, () -> s.execute("DELETE FROM app_user"));
      }
    }
    var id = (UUID) db.one("SELECT user_id FROM app_user").get("user_id");
    db.run(
        "INSERT INTO learning_setup(user_id,current_course_id,current_level_id) VALUES(?,?,?)",
        id,
        UUID.randomUUID(),
        UUID.randomUUID());
    assertEquals(1, db.count("SELECT count(*) FROM learning_setup"));
    assertThrows(
        ApiError.class,
        () -> db.run("INSERT INTO learner_profile(user_id) VALUES(?)", UUID.randomUUID()));
  }

  @Test
  void cohortPagingFiltersKeepGlobalCountAndBlockedLearners() throws Exception {
    UUID course = UUID.randomUUID(), level = UUID.randomUUID();
    for (int i = 0; i < 3; i++) {
      UUID id = UUID.randomUUID();
      db.run("INSERT INTO app_user(user_id,email_normalized,password_hash,role,status)"
          + " VALUES(?,?,?,'LEARNER',?)", id, "cohort" + i + "@example.com", "test-hash",
          i == 0 ? "BLOCKED" : "ACTIVE");
      if (i < 2) db.run("INSERT INTO learning_setup(user_id,current_course_id,current_level_id)"
          + " VALUES(?,?,?)", id, course, level);
    }
    var seen = new HashSet<String>();
    for (int page = 0; page < 2; page++) {
      var response = cohortRequest("?courseId=" + course + "&levelId=" + level + "&size=1&page=" + page);
      assertEquals(200, response.statusCode(), response.body());
      var body = Json.map(response.body());
      assertEquals(3, ((Number) body.get("totalLearnersGlobal")).intValue());
      assertEquals(2, ((Number) body.get("totalElements")).intValue());
      var items = (List<?>) body.get("items");
      assertEquals(1, items.size());
      assertTrue(seen.add(((Map<?, ?>) items.getFirst()).get("userId").toString()));
    }
    var empty = Json.map(cohortRequest("?courseId=" + UUID.randomUUID()).body());
    assertEquals(3, ((Number) empty.get("totalLearnersGlobal")).intValue());
    assertEquals(0, ((Number) empty.get("totalElements")).intValue());
    assertEquals(400, cohortRequest("?courseId=invalid").statusCode());
  }

  HttpResponse<String> cohortRequest(String query) throws Exception {
    return http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port
        + "/internal/v1/learners/cohort" + query))
        .header("Authorization", "Bearer " + TestSupport.caller("progress").service("user"))
        .GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void internalSetupAndCohortRequireOwnerCallers() throws Exception {
    csrf();
    UUID uid = Input.id(registration("internal@example.com").get("userId").toString());
    UUID course = UUID.randomUUID(), level = UUID.randomUUID();
    db.run(
        "INSERT INTO learning_setup(user_id,current_course_id,current_level_id) VALUES(?,?,?)",
        uid,
        course,
        level);
    db.run("UPDATE app_user SET status='BLOCKED' WHERE user_id=?", uid);
    var request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:" + port + "/internal/v1/users/" + uid + "/learning-setup"))
            .header("Authorization", "Bearer " + TestSupport.caller("assessment").service("user"))
            .GET()
            .build();
    var response = http.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    assertEquals(course.toString(), Json.map(response.body()).get("courseId"));
    request =
        HttpRequest.newBuilder(
                URI.create(
                    "http://localhost:"
                        + port
                        + "/internal/v1/learners/cohort?courseId="
                        + course
                        + "&levelId="
                        + level))
            .header("Authorization", "Bearer " + TestSupport.caller("progress").service("user"))
            .GET()
            .build();
    response = http.send(request, HttpResponse.BodyHandlers.ofString());
    assertEquals(200, response.statusCode(), response.body());
    assertEquals(1, ((List<?>) Json.map(response.body()).get("items")).size());
    assertEquals(403, call("GET", "/internal/v1/learners/cohort", null, null).statusCode());
  }
}
