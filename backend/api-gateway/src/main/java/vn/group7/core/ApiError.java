package vn.group7.core;

import java.util.*;

public class ApiError extends RuntimeException {
  public final int status;
  public final String code;
  public final List<Map<String, Object>> errors;
  public final Map<String, Object> extra;

  public ApiError(int s, String c, String m) {
    this(s, c, m, List.of(), Map.of());
  }

  public ApiError(int s, String c, String m, List<Map<String, Object>> e, Map<String, Object> x) {
    super(m);
    status = s;
    code = c;
    errors = e;
    extra = x;
  }

  public Map<String, Object> body(String trace) {
    var b = new LinkedHashMap<String, Object>();
    b.put("code", code);
    b.put("message", getMessage());
    b.put("traceId", trace);
    b.put("errors", errors);
    b.putAll(extra);
    return b;
  }

  public static ApiError bad(String f, String m) {
    return new ApiError(
        400,
        "VALIDATION_ERROR",
        "Dữ liệu không hợp lệ",
        List.of(Map.of("field", f, "code", "INVALID", "message", m)),
        Map.of());
  }

  public static ApiError missing() {
    return new ApiError(404, "NOT_FOUND", "Không tìm thấy tài nguyên");
  }

  public static ApiError dependency() {
    return new ApiError(503, "DEPENDENCY_UNAVAILABLE", "Dịch vụ nguồn chưa sẵn sàng");
  }
}
