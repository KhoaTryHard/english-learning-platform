package vn.group7.core;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class Errors {
  @ExceptionHandler({
    org.springframework.web.servlet.resource.NoResourceFoundException.class,
    org.springframework.web.servlet.NoHandlerFoundException.class
  })
  ResponseEntity<?> notFound(HttpServletRequest r) {
    return api(ApiError.missing(), r);
  }

  @ExceptionHandler({
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
    org.springframework.web.multipart.MultipartException.class
  })
  ResponseEntity<?> invalidRequest(HttpServletRequest r) {
    return api(ApiError.bad("request", "Request không hợp lệ"), r);
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  ResponseEntity<?> unsupported(HttpServletRequest r) {
    return api(new ApiError(415, "UNSUPPORTED_FILE_TYPE", "Media type không hỗ trợ"), r);
  }

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
