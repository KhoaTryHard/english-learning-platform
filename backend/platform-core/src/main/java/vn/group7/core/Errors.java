package vn.group7.core;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class Errors {
  @ExceptionHandler(ApiError.class)
  ResponseEntity<?> api(ApiError e, HttpServletRequest r) {
    return ResponseEntity.status(e.status).body(e.body(trace(r)));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<?> malformed(HttpServletRequest r) {
    return api(ApiError.bad("body", "JSON không hợp lệ"), r);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<?> size(HttpServletRequest r) {
    return api(new ApiError(413, "FILE_TOO_LARGE", "File quá lớn"), r);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> other(Exception e, HttpServletRequest r) {
    if (e instanceof org.springframework.web.HttpRequestMethodNotSupportedException)
      return api(new ApiError(405, "METHOD_NOT_ALLOWED", "Phương thức không hỗ trợ"), r);
    return api(new ApiError(500, "INTERNAL_ERROR", "Không thể xử lý yêu cầu"), r);
  }

  static String trace(HttpServletRequest r) {
    var v = r.getAttribute("traceId");
    return v == null ? java.util.UUID.randomUUID().toString() : v.toString();
  }
}
