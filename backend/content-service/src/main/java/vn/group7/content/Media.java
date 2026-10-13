package vn.group7.content;

import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.group7.core.*;

@Component
public class Media {
  private final Set<String> hosts;
  private final HttpClient http =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(2))
          .followRedirects(HttpClient.Redirect.NEVER)
          .build();

  public Media(@Value("${platform.media-hosts:}") String h) {
    hosts = new HashSet<>(Arrays.asList(h.toLowerCase(Locale.ROOT).split(",")));
  }

  public URI allowed(String url) {
    try {
      var u = URI.create(url);
      if (!"https".equals(u.getScheme())
          || u.getHost() == null
          || u.getUserInfo() != null
          || u.getFragment() != null
          || (u.getPort() != -1 && u.getPort() != 443)
          || !hosts.contains(u.getHost().toLowerCase(Locale.ROOT))) throw new Exception();
      for (var a : InetAddress.getAllByName(u.getHost()))
        if (a.isAnyLocalAddress()
            || a.isLoopbackAddress()
            || a.isLinkLocalAddress()
            || a.isSiteLocalAddress()
            || a.isMulticastAddress()
            || a.getHostAddress().startsWith("fc")
            || a.getHostAddress().startsWith("fd")) throw new Exception();
      return u;
    } catch (Exception e) {
      throw ApiError.bad("resourceUrl", "Cần HTTPS trên media host công khai được cấu hình");
    }
  }

  public boolean available(String url) {
    try {
      var u = allowed(url);
      var r =
          http.send(
              HttpRequest.newBuilder(u)
                  .timeout(Duration.ofSeconds(3))
                  .method("HEAD", HttpRequest.BodyPublishers.noBody())
                  .build(),
              HttpResponse.BodyHandlers.discarding());
      return r.statusCode() == 200
          && r.headers().firstValue("Content-Type").orElse("").startsWith("audio/");
    } catch (Exception e) {
      return false;
    }
  }
}
