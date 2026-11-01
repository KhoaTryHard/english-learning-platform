package vn.group7.api;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** MVC intercepts CORS preflight before controller invocation; use the same route policy first. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class GatewayPreflight extends OncePerRequestFilter {
  private final Gateway gateway;

  public GatewayPreflight(Gateway gateway) {
    this.gateway = gateway;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
      FilterChain chain) throws ServletException, IOException {
    if ("OPTIONS".equals(request.getMethod()) && request.getRequestURI().startsWith("/api/v1/")) {
      gateway.proxy(request, response);
    } else {
      chain.doFilter(request, response);
    }
  }
}
