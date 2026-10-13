package vn.group7.api;

import jakarta.servlet.http.*;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import vn.group7.core.*;

@RestController
public class Gateway {
  private final TokenCodec tokens;
  private final OwnerClient client;
  private final String user, content, origin;
  private final List<Map<String, Object>> routes;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(2))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public Gateway(
      TokenCodec t,
      OwnerClient c,
      @Value("${platform.user-url}") String u,
      @Value("${platform.content-url}") String co,
      @Value("${platform.frontend-origin}") String o)
      throws Exception {
    tokens = t;
    client = c;
    user = u;
    content = co;
    origin = o;
    routes =
        Json.M.readValue(
            getClass().getResourceAsStream("/public-routes.json"),
            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
  }

  static String cookie(HttpServletRequest r, String name) {
    if (r.getCookies() != null)
      for (var c : r.getCookies()) if (c.getName().equals(name)) return c.getValue();
    return null;
  }

  boolean match(String pattern, String path) {
    return path.matches(pattern.replaceAll("\\{[^}]+\\}", "[^/]+"));
  }

  @RequestMapping("/api/v1/**")
  void proxy(HttpServletRequest r, HttpServletResponse out) throws IOException {
    String trace = UUID.randomUUID().toString();
    r.setAttribute("traceId", trace);
    out.setHeader("X-Trace-Id", trace);
    out.setHeader("X-Content-Type-Options", "nosniff");
    out.setHeader("Cache-Control", "no-store");
    try {
      String requestOrigin = r.getHeader("Origin");
      if (requestOrigin != null && !origin.equals(requestOrigin))
        throw new ApiError(403, "CSRF_INVALID", "Origin không hợp lệ");
      if (origin.equals(requestOrigin)) {
        out.setHeader("Access-Control-Allow-Origin", origin);
        out.setHeader("Access-Control-Allow-Credentials", "true");
        out.setHeader("Vary", "Origin");
      }
      String path = r.getRequestURI();
      if (path.contains("%") || path.contains(";") || path.contains(".."))
        throw ApiError.bad("path", "Path không hợp lệ");
      boolean unsafe = !Set.of("GET", "HEAD", "OPTIONS").contains(r.getMethod());
      if (unsafe && !origin.equals(requestOrigin))
        throw new ApiError(403, "CSRF_INVALID", "Cần Origin được cấu hình");
      var candidates = routes.stream().filter(x -> match((String) x.get("path"), path)).toList();
      if (candidates.isEmpty()) throw ApiError.missing();
      if (r.getMethod().equals("OPTIONS")) {
        out.setHeader(
            "Access-Control-Allow-Methods",
            String.join(",", candidates.stream().map(x -> x.get("method").toString()).toList()));
        out.setHeader("Access-Control-Allow-Headers", "Content-Type,X-CSRF-Token");
        out.setStatus(204);
        return;
      }
      var route =
          candidates.stream()
              .filter(x -> x.get("method").equals(r.getMethod()))
              .findFirst()
              .orElseThrow(
                  () -> new ApiError(405, "METHOD_NOT_ALLOWED", "Phương thức không hỗ trợ"));
      String owner = route.get("owner").equals("User Service") ? "user" : "content";
      boolean anon = ((List<?>) route.get("roles")).contains("PUBLIC");
      Map<String, Object> principal = null;
      if (!anon) {
        var check =
            Json.nullable(
                "token",
                cookie(r, "access_token"),
                "requestMethod",
                r.getMethod(),
                "csrfSessionId",
                cookie(r, "csrf_session"),
                "csrfToken",
                r.getHeader("X-CSRF-Token"));
        if (check.get("token") == null) throw new ApiError(401, "SESSION_INVALID", "Chưa xác thực");
        principal = client.call(user, "/internal/v1/sessions/validate", "user", check);
        String role = principal.get("role").toString();
        var roles = (List<?>) route.get("roles");
        if (!roles.contains("AUTHENTICATED") && !roles.contains(role))
          throw new ApiError(403, "FORBIDDEN", "Không có quyền");
      }
      byte[] body = r.getInputStream().readNBytes(6 * 1024 * 1024 + 1);
      if (body.length > 6 * 1024 * 1024)
        throw new ApiError(413, "FILE_TOO_LARGE", "Request quá lớn");
      var b =
          HttpRequest.newBuilder(
                  URI.create(
                      (owner.equals("user") ? user : content)
                          + path
                          + (r.getQueryString() == null ? "" : "?" + r.getQueryString())))
              .timeout(Duration.ofSeconds(30))
              .header("Authorization", "Bearer " + tokens.service(owner))
              .header("X-Trace-Id", trace)
              .method(r.getMethod(), HttpRequest.BodyPublishers.ofByteArray(body));
      for (String h : List.of("Content-Type", "Origin", "X-CSRF-Token")) {
        String v = r.getHeader(h);
        if (v != null) b.header(h, v);
      }
      if (owner.equals("user") && r.getHeader("Cookie") != null)
        b.header("Cookie", r.getHeader("Cookie"));
      if (principal != null)
        b.header(
            "X-Gateway-Principal",
            tokens.sign(
                "principal",
                owner,
                Map.of(
                    "sub",
                    principal.get("userId"),
                    "role",
                    principal.get("role"),
                    "sessionJti",
                    principal.get("jti"),
                    "method",
                    r.getMethod(),
                    "path",
                    path),
                Instant.now().plusSeconds(15)));
      var response = http.send(b.build(), HttpResponse.BodyHandlers.ofByteArray());
      out.setStatus(response.statusCode());
      for (String h : List.of("Content-Type", "Content-Disposition", "Set-Cookie"))
        for (String v : response.headers().allValues(h)) out.addHeader(h, v);
      out.getOutputStream().write(response.body());
    } catch (ApiError e) {
      out.setStatus(e.status);
      out.setContentType("application/json;charset=UTF-8");
      out.getWriter().write(Json.text(e.body(trace)));
    } catch (Exception e) {
      var error = ApiError.dependency();
      out.setStatus(503);
      out.setContentType("application/json;charset=UTF-8");
      out.getWriter().write(Json.text(error.body(trace)));
    }
  }
}
