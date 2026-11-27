package vn.group7.core;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

public record Principal(UUID userId, String role, String jti) {
  public static Principal get(HttpServletRequest r) {
    var p = (Principal) r.getAttribute("principal");
    if (p == null) throw new ApiError(401, "SESSION_INVALID", "Chưa xác thực");
    return p;
  }
}
