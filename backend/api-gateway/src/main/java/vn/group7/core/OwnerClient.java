package vn.group7.core;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class OwnerClient {
  private final TokenCodec tokens;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(2))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public OwnerClient(TokenCodec t) {
    tokens = t;
  }

  public Map<String, Object> get(String base, String path, String owner) {
    return call(base, path, owner, null);
  }

  public Map<String, Object> call(
      String base, String path, String owner, Map<String, Object> body) {
    try {
      var b =
          HttpRequest.newBuilder(URI.create(base + path))
              .timeout(Duration.ofSeconds(5))
              .header("Authorization", "Bearer " + tokens.service(owner))
              .header("Content-Type", "application/json");
      if (body == null) b.GET();
      else b.POST(HttpRequest.BodyPublishers.ofString(Json.text(body)));
      var r = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
      if (r.statusCode() == 404) throw ApiError.missing();
      if (r.statusCode() == 401 || r.statusCode() == 403) {
        if (path.equals("/internal/v1/sessions/validate")) {
          var e = Json.map(r.body());
          throw new ApiError(r.statusCode(), e.get("code").toString(), e.get("message").toString());
        }
        throw ApiError.dependency();
      }
      if (r.statusCode() != 200) throw ApiError.dependency();
      return Json.map(r.body());
    } catch (ApiError e) {
      throw e;
    } catch (Exception e) {
      throw ApiError.dependency();
    }
  }
}
