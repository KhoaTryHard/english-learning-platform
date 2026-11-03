package vn.group7.user;

import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.session.Session;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.web.bind.annotation.*;
import vn.group7.core.*;

@RestController
@EnableScheduling
public class UserController {
  private final Db db;
  private final TokenCodec tokens;
  private final BCryptPasswordEncoder encoder;
  private final JdbcIndexedSessionRepository sessions;
  private final OwnerClient client;
  private final String contentUrl, origin;
  private final boolean secure;
  private final int minutes;

  public UserController(
      Db d,
      TokenCodec t,
      BCryptPasswordEncoder e,
      JdbcIndexedSessionRepository s,
      OwnerClient c,
      @Value("${platform.content-url}") String url,
      @Value("${platform.frontend-origin}") String o,
      @Value("${platform.secure-cookies}") boolean sec,
      @Value("${platform.jwt-minutes}") int min) {
    db = d;
    tokens = t;
    encoder = e;
    sessions = s;
    client = c;
    contentUrl = url;
    origin = o;
    secure = sec;
    minutes = min;
  }

  static String cookie(HttpServletRequest r, String name) {
    if (r.getCookies() != null)
      for (var c : r.getCookies()) if (c.getName().equals(name)) return c.getValue();
    return null;
  }

  private void origin(HttpServletRequest r) {
    if (!origin.equals(r.getHeader("Origin")))
      throw new ApiError(403, "CSRF_INVALID", "Origin không hợp lệ");
  }

  private void csrf(HttpServletRequest r) {
    origin(r);
    var s = r.getSession(false);
    if (s == null || !equal((String) s.getAttribute("csrf"), r.getHeader("X-CSRF-Token")))
      throw new ApiError(403, "CSRF_INVALID", "CSRF không hợp lệ");
  }

  static boolean equal(String a, String b) {
    return a != null
        && b != null
        && java.security.MessageDigest.isEqual(
            a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }

  @GetMapping("/api/v1/auth/csrf")
  ResponseEntity<?> csrfToken(HttpServletRequest r) {
    var s = r.getSession();
    String token = (String) s.getAttribute("csrf");
    if (token == null) {
      token = UUID.randomUUID() + "-" + UUID.randomUUID();
      s.setAttribute("csrf", token);
    }
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(Map.of("csrfToken", token, "headerName", "X-CSRF-Token"));
  }

  static String email(Object o) {
    return Input.text(o, 254, false).toLowerCase(Locale.ROOT);
  }

  static String password(Object o) {
    if (!(o instanceof String p)
        || p.codePointCount(0, p.length()) < 15
        || p.getBytes(StandardCharsets.UTF_8).length > 72)
      throw ApiError.bad("password", "Cần tối thiểu 15 ký tự Unicode và tối đa 72 byte UTF-8");
    return (String) o;
  }

  private Map<String, Object> user(UUID id) {
    return db.one(
        "SELECT u.*,p.display_name FROM app_user u LEFT JOIN learner_profile p USING(user_id) WHERE"
            + " user_id=?",
        id);
  }

  private Map<String, Object> profile(Map<String, Object> u) {
    return Json.nullable(
        "userId",
        u.get("user_id"),
        "email",
        u.get("email_normalized"),
        "fullName",
        u.get("display_name"),
        "role",
        u.get("role"),
        "status",
        u.get("status"));
  }

  @PostMapping("/api/v1/auth/register")
  ResponseEntity<?> register(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    csrf(r);
    Input.check("RegisterRequest", b);
    String em = email(b.get("email")),
        hash = encoder.encode(password(b.get("password"))),
        name = Input.text(b.get("fullName"), 100, true);
    UUID id = UUID.randomUUID();
    try {
      db.tx(
          () -> {
            db.run(
                "INSERT INTO app_user(user_id,email_normalized,password_hash,role,status)"
                    + " VALUES(?,?,?,'LEARNER','ACTIVE')",
                id,
                em,
                hash);
            db.run("INSERT INTO learner_profile(user_id,display_name) VALUES(?,?)", id, name);
            return null;
          });
    } catch (ApiError e) {
      if (e.status == 409)
        throw new ApiError(
            409, "REGISTRATION_CONFLICT", "Không thể tạo tài khoản với thông tin đã cung cấp");
      throw e;
    }
    return ResponseEntity.status(201).body(profile(user(id)));
  }

  @PostMapping("/api/v1/auth/login")
  ResponseEntity<?> login(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    csrf(r);
    Input.check("LoginRequest", b);
    var found = db.rows("SELECT * FROM app_user WHERE email_normalized=?", email(b.get("email")));
    String pw = (String) b.get("password");
    if (found.isEmpty()
        || !"ACTIVE".equals(found.getFirst().get("status"))
        || pw.getBytes(StandardCharsets.UTF_8).length > 72
        || !encoder.matches(pw, found.getFirst().get("password_hash").toString()))
      throw new ApiError(401, "INVALID_CREDENTIALS", "Thông tin đăng nhập không hợp lệ");
    var u = found.getFirst();
    Instant expiry = Instant.now().plusSeconds(minutes * 60L);
    String jti = UUID.randomUUID().toString();
    String token =
        tokens.sign(
            "user",
            "api",
            Map.of("sub", u.get("user_id").toString(), "role", u.get("role"), "jti", jti),
            expiry);
    r.getSession().invalidate();
    var session = r.getSession(true);
    session.setMaxInactiveInterval(minutes * 60);
    session.setAttribute("jti", jti);
    session.setAttribute("jwtExpiry", expiry.getEpochSecond());
    session.setAttribute("csrf", UUID.randomUUID() + "-" + UUID.randomUUID());
    var ck =
        ResponseCookie.from("access_token", token)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/api/v1")
            .maxAge(minutes * 60L)
            .build();
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, ck.toString())
        .cacheControl(CacheControl.noStore())
        .body(
            Map.of("user", profile(user((UUID) u.get("user_id"))), "expiresAt", expiry.toString()));
  }

  private JWTClaimsSet validate(String token) {
    var c = tokens.verify(token, "user", "api");
    UUID id, jti;
    Map<String, Object> u;
    try {
      id = Input.id(c.getSubject());
      jti = Input.id(c.getJWTID());
      u = user(id);
    } catch (ApiError e) {
      if (e.status == 503) throw e;
      throw new ApiError(401, "SESSION_INVALID", "Phiên không hợp lệ");
    }
    if (!"ACTIVE".equals(u.get("status"))
        || !Objects.equals(u.get("role"), TokenCodec.string(c, "role"))
        || db.count("SELECT count(*) FROM revoked_token WHERE jti=?", jti) > 0)
      throw new ApiError(401, "SESSION_INVALID", "Phiên không hợp lệ");
    return c;
  }

  @PostMapping("/internal/v1/sessions/validate")
  Map<String, Object> validateRequest(@RequestBody Map<String, Object> b) {
    Set<String> allowed = Set.of("token", "requestMethod", "csrfSessionId", "csrfToken");
    if (!allowed.containsAll(b.keySet())
        || !(b.get("token") instanceof String)
        || !(b.get("requestMethod") instanceof String))
      throw ApiError.bad("request", "Request internal v0.1.1 không hợp lệ");
    String method = (String) b.get("requestMethod");
    if (!Set.of("GET", "HEAD", "OPTIONS", "POST", "PUT", "PATCH", "DELETE").contains(method))
      throw ApiError.bad("requestMethod", "Method không hợp lệ");
    var c = validate((String) b.get("token"));
    if (!Set.of("GET", "HEAD", "OPTIONS").contains(method)) {
      var sid = b.get("csrfSessionId");
      Session s;
      try {
        s = sid instanceof String ? sessions.findById((String) sid) : null;
      } catch (org.springframework.dao.DataAccessException unavailableSessionStore) {
        throw ApiError.dependency();
      }
      Long exp = s == null ? null : s.getAttribute("jwtExpiry");
      if (s == null
          || !c.getJWTID().equals(s.getAttribute("jti"))
          || exp == null
          || exp <= Instant.now().getEpochSecond()
          || !equal(
              s.getAttribute("csrf"),
              b.get("csrfToken") instanceof String ? (String) b.get("csrfToken") : null))
        throw new ApiError(403, "CSRF_INVALID", "CSRF không hợp lệ");
    }
    return Map.of(
        "userId",
        c.getSubject(),
        "role",
        TokenCodec.string(c, "role"),
        "jti",
        c.getJWTID(),
        "expiresAt",
        c.getExpirationTime().toInstant().toString(),
        "accountStatus",
        "ACTIVE");
  }

  @PostMapping("/api/v1/auth/logout")
  ResponseEntity<?> logout(HttpServletRequest r) {
    csrf(r);
    var c = validate(cookie(r, "access_token"));
    db.run(
        "INSERT INTO revoked_token(jti,expires_at) VALUES(?,?) ON CONFLICT(jti) DO NOTHING",
        Input.id(c.getJWTID()),
        java.sql.Timestamp.from(c.getExpirationTime().toInstant()));
    var s = r.getSession(false);
    if (s != null) s.invalidate();
    return ResponseEntity.noContent()
        .header(
            HttpHeaders.SET_COOKIE,
            ResponseCookie.from("access_token", "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/api/v1")
                .maxAge(0)
                .build()
                .toString())
        .build();
  }

  @GetMapping("/api/v1/me/profile")
  Map<String, Object> me(HttpServletRequest r) {
    return profile(user(Principal.get(r).userId()));
  }

  @PatchMapping("/api/v1/me/profile")
  Map<String, Object> patch(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    Input.check("ProfilePatch", b);
    UUID id = Principal.get(r).userId();
    db.run(
        "UPDATE learner_profile SET display_name=? WHERE user_id=?",
        Input.text(b.get("fullName"), 100, true),
        id);
    return profile(user(id));
  }

  private Map<String, Object> setup(UUID id) {
    var rows = db.rows("SELECT * FROM learning_setup WHERE user_id=?", id);
    if (rows.isEmpty()) return Json.nullable("courseId", null, "levelId", null, "updatedAt", null);
    var s = rows.getFirst();
    return Map.of(
        "courseId",
        s.get("current_course_id"),
        "levelId",
        s.get("current_level_id"),
        "updatedAt",
        Db.instant(s.get("updated_at")));
  }

  @GetMapping("/api/v1/me/learning-setup")
  Map<String, Object> setup(HttpServletRequest r) {
    return setup(Principal.get(r).userId());
  }

  @GetMapping("/internal/v1/users/{userId}/learning-setup")
  Map<String, Object> internalSetup(@PathVariable String userId) {
    UUID id = Input.id(userId);
    user(id);
    return setup(id);
  }

  @PutMapping("/api/v1/me/learning-setup")
  Map<String, Object> setup(@RequestBody Map<String, Object> b, HttpServletRequest r) {
    Input.check("SetupWrite", b);
    UUID course = Input.id((String) b.get("courseId")), level = Input.id((String) b.get("levelId"));
    var ctx = client.get(contentUrl, "/internal/v1/courses/" + course + "/context", "content");
    if (!course.toString().equals(ctx.get("courseId"))
        || !Boolean.TRUE.equals(ctx.get("published")))
      throw new ApiError(409, "LEARNING_SETUP_MISMATCH", "Course không khả dụng");
    if (!(ctx.get("levels") instanceof List<?> levels)
        || levels.stream()
            .noneMatch(
                x ->
                    x instanceof Map<?, ?> m
                        && level.toString().equals(m.get("levelId"))
                        && course.toString().equals(m.get("courseId"))))
      throw new ApiError(409, "LEARNING_SETUP_MISMATCH", "Level không thuộc Course");
    UUID id = Principal.get(r).userId();
    db.run(
        "INSERT INTO learning_setup(user_id,current_course_id,current_level_id) VALUES(?,?,?) ON"
            + " CONFLICT(user_id) DO UPDATE SET"
            + " current_course_id=excluded.current_course_id,current_level_id=excluded.current_level_id",
        id,
        course,
        level);
    return setup(id);
  }

  @GetMapping("/api/v1/admin/users")
  Map<String, Object> users(
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size,
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String status) {
    int p = Input.page(page), s = Input.size(size);
    if (status != null && !Set.of("ACTIVE", "BLOCKED").contains(status))
      throw ApiError.bad("status", "Status không hợp lệ");
    String where =
        " WHERE (?::varchar IS NULL OR u.status=?) AND (?::varchar IS NULL OR u.email_normalized"
            + " ILIKE ? OR p.display_name ILIKE ?)";
    String term = q == null ? null : "%" + q + "%";
    Object[] args = {status, status, q, term, term};
    long total =
        db.count(
            "SELECT count(*) FROM app_user u LEFT JOIN learner_profile p USING(user_id)" + where,
            args);
    var a = new ArrayList<>(Arrays.asList(args));
    a.add(s);
    a.add((long) p * s);
    return Map.of(
        "items",
        db
            .rows(
                "SELECT u.*,p.display_name FROM app_user u LEFT JOIN learner_profile p"
                    + " USING(user_id)"
                    + where
                    + " ORDER BY user_id LIMIT ? OFFSET ?",
                a.toArray())
            .stream()
            .map(this::profile)
            .toList(),
        "page",
        p,
        "size",
        s,
        "totalElements",
        total,
        "totalPages",
        (total + s - 1) / s);
  }

  @GetMapping("/api/v1/admin/users/{id}")
  Map<String, Object> adminUser(@PathVariable String id) {
    return profile(user(Input.id(id)));
  }

  @PatchMapping("/api/v1/admin/users/{id}")
  Map<String, Object> adminPatch(@PathVariable String id, @RequestBody Map<String, Object> b) {
    Input.check("AdminUserPatch", b);
    UUID uid = Input.id(id);
    db.tx(
        () -> {
          var u = db.one("SELECT * FROM app_user WHERE user_id=? FOR UPDATE", uid);
          if (!"LEARNER".equals(u.get("role")))
            throw new ApiError(409, "RESOURCE_CONFLICT", "Chỉ quản lý Learner");
          if (b.containsKey("status"))
            db.run("UPDATE app_user SET status=? WHERE user_id=?", b.get("status"), uid);
          if (b.containsKey("fullName"))
            db.run(
                "UPDATE learner_profile SET display_name=? WHERE user_id=?",
                Input.text(b.get("fullName"), 100, true),
                uid);
          return null;
        });
    return profile(user(uid));
  }

  @GetMapping("/internal/v1/learners/cohort")
  Map<String, Object> cohort(
      @RequestParam(required = false) String courseId,
      @RequestParam(required = false) String levelId,
      @RequestParam(required = false) Integer page,
      @RequestParam(required = false) Integer size) {
    int p = Input.page(page), s = Input.size(size == null ? 100 : size);
    UUID course = courseId == null ? null : Input.id(courseId),
        level = levelId == null ? null : Input.id(levelId);
    String where =
        " WHERE u.role='LEARNER' AND (?::uuid IS NULL OR ls.current_course_id=?) AND (?::uuid IS"
            + " NULL OR ls.current_level_id=?)";
    Object[] a = {course, course, level, level};
    long n =
        db.count(
            "SELECT count(*) FROM app_user u LEFT JOIN learning_setup ls USING(user_id)" + where,
            a);
    var args = new ArrayList<>(Arrays.asList(a));
    args.add(s);
    args.add((long) p * s);
    var rows =
        db.rows(
            "SELECT u.user_id,ls.current_course_id,ls.current_level_id FROM app_user u LEFT JOIN"
                + " learning_setup ls USING(user_id)"
                + where
                + " ORDER BY u.user_id LIMIT ? OFFSET ?",
            args.toArray());
    return Map.of(
        "items",
        rows.stream()
            .map(
                x ->
                    Json.nullable(
                        "userId",
                        x.get("user_id"),
                        "currentCourseId",
                        x.get("current_course_id"),
                        "currentLevelId",
                        x.get("current_level_id")))
            .toList(),
        "page",
        p,
        "size",
        s,
        "totalElements",
        n,
        "totalLearnersGlobal",
        db.count("SELECT count(*) FROM app_user WHERE role='LEARNER'"),
        "generatedAt",
        Instant.now().toString());
  }

  @Scheduled(fixedDelay = 60000)
  void clean() {
    db.run("DELETE FROM revoked_token WHERE expires_at<=now()");
  }
}
