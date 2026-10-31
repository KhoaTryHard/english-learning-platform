package vn.group7.core;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Assessment adapter for the signed owner handoff used by Khoa's Gateway. */
@Component
public class ServiceGuard extends OncePerRequestFilter {
  private final TokenCodec tokens;
  private final String self;

  public ServiceGuard(
      TokenCodec tokens,
      @org.springframework.beans.factory.annotation.Value("${platform.service}") String self) {
    this.tokens = tokens;
    this.self = self;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String trace = request.getHeader("X-Trace-Id");
    try {
      Input.id(trace);
    } catch (Exception invalid) {
      trace = UUID.randomUUID().toString();
    }
    request.setAttribute("traceId", trace);
    try {
      String path = request.getRequestURI();
      if (path.equals("/actuator/health")) {
        chain.doFilter(request, response);
        return;
      }
      var identity =
          tokens.verify(TokenCodec.bearer(request.getHeader("Authorization")), "service", self);
      String caller = identity.getSubject();
      if (path.startsWith("/internal/v1/")) {
        if (!path.matches("/internal/v1/lessons/[^/]+/exercise-readiness")
            || !"content".equals(caller))
          throw new ApiError(403, "FORBIDDEN", "Caller is not allowed for this internal API");
      } else {
        if (!"api".equals(caller))
          throw new ApiError(403, "FORBIDDEN", "Request must pass through Gateway");
        var principal = tokens.verify(request.getHeader("X-Gateway-Principal"), "principal", self);
        if (!request.getMethod().equals(TokenCodec.string(principal, "method"))
            || !path.equals(TokenCodec.string(principal, "path")))
          throw new ApiError(401, "SESSION_INVALID", "Assertion is bound to a different request");
        String role = TokenCodec.string(principal, "role"),
            session = TokenCodec.string(principal, "sessionJti");
        UUID user;
        try {
          user = Input.id(principal.getSubject());
          Input.id(session);
        } catch (ApiError invalid) {
          throw new ApiError(401, "SESSION_INVALID", "Invalid principal identifiers");
        }
        if (!Set.of("LEARNER", "ADMIN").contains(role))
          throw new ApiError(401, "SESSION_INVALID", "Invalid role");
        String expected = path.startsWith("/api/v1/admin/") ? "ADMIN" : "LEARNER";
        if (!expected.equals(role))
          throw new ApiError(403, "FORBIDDEN", "Role is not allowed for this API");
        request.setAttribute("principal", new Principal(user, role, session));
      }
      chain.doFilter(request, response);
    } catch (ApiError error) {
      response.setStatus(error.status);
      response.setContentType("application/json;charset=UTF-8");
      response.getWriter().write(Json.text(error.body(trace)));
    }
  }
}
