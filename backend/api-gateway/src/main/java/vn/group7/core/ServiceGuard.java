package vn.group7.core;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ServiceGuard extends OncePerRequestFilter {
  private final TokenCodec tokens;
  private final String self;

  public ServiceGuard(TokenCodec t, @Value("${platform.service}") String s) {
    tokens = t;
    self = s;
  }

  protected void doFilterInternal(HttpServletRequest r, HttpServletResponse res, FilterChain chain)
      throws ServletException, IOException {
    if (self.equals("api")) {
      chain.doFilter(r, res);
      return;
    }
    String trace = r.getHeader("X-Trace-Id");
    try {
      Input.id(trace);
    } catch (Exception e) {
      trace = UUID.randomUUID().toString();
    }
    r.setAttribute("traceId", trace);
    try {
      String path = r.getRequestURI();
      if (path.equals("/actuator/health")) {
        chain.doFilter(r, res);
        return;
      }
      var identity =
          tokens.verify(TokenCodec.bearer(r.getHeader("Authorization")), "service", self);
      String caller = identity.getSubject();
      r.setAttribute("caller", caller);
      if (path.startsWith("/internal/v1/")) {
        Set<String> allowed =
            path.equals("/internal/v1/sessions/validate")
                ? Set.of("api")
                : path.matches("/internal/v1/users/[^/]+/learning-setup")
                    ? Set.of("assessment", "progress")
                    : path.equals("/internal/v1/learners/cohort")
                            || path.equals("/internal/v1/courses/contexts")
                        ? Set.of("progress")
                        : path.matches("/internal/v1/courses/[^/]+/context")
                            ? Set.of("user", "assessment", "progress")
                            : path.matches("/internal/v1/lessons/[^/]+/context")
                                ? Set.of("assessment")
                                : Set.of();
        if (!allowed.contains(caller))
          throw new ApiError(403, "FORBIDDEN", "Caller không có quyền");
      } else {
        if (!caller.equals("api")) throw new ApiError(403, "FORBIDDEN", "Yêu cầu phải qua Gateway");
        boolean anon =
            path.equals("/api/v1/auth/csrf")
                || path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/register");
        if (!anon) {
          var p = tokens.verify(r.getHeader("X-Gateway-Principal"), "principal", self);
          if (!r.getMethod().equals(TokenCodec.string(p, "method"))
              || !path.equals(TokenCodec.string(p, "path")))
            throw new ApiError(401, "SESSION_INVALID", "Assertion sai request");
          String role = TokenCodec.string(p, "role");
          if (!Set.of("LEARNER", "ADMIN").contains(role))
            throw new ApiError(401, "SESSION_INVALID", "Role không hợp lệ");
          if (path.startsWith("/api/v1/admin/") && !role.equals("ADMIN"))
            throw new ApiError(403, "FORBIDDEN", "Chỉ Admin");
          if (!path.startsWith("/api/v1/admin/")
              && !path.startsWith("/api/v1/auth/")
              && !role.equals("LEARNER")) throw new ApiError(403, "FORBIDDEN", "Chỉ Learner");
          r.setAttribute(
              "principal",
              new Principal(Input.id(p.getSubject()), role, TokenCodec.string(p, "sessionJti")));
        }
      }
      chain.doFilter(r, res);
    } catch (ApiError e) {
      res.setStatus(e.status);
      res.setContentType("application/json;charset=UTF-8");
      res.getWriter().write(Json.text(e.body(trace)));
    }
  }
}
