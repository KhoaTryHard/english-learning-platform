import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import vn.group7.core.*;

/** TEST probe signs with the actual application TokenCodec; never prints a JWT. */
public class OwnerSourceProbe {
  public static void main(String[] args) throws Exception {
    Map<String, Object> config = Json.map(new String(System.in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
    String caller = config.get("caller").toString();
    String owner = config.get("owner").toString();
    String path = config.get("path").toString();
    if (!path.startsWith("/internal/v1/") || path.contains("sessions"))
      throw new IllegalArgumentException("Only owner source APIs are supported");
    Path root = Path.of(args[0]);
    String token = new TokenCodec(root.resolve(".secrets/keys/public").toString(), caller,
        root.resolve(".secrets/keys/" + caller + ".private.pem").toString()).service(owner);
    if (Boolean.TRUE.equals(config.get("tamper"))) {
      int at = token.lastIndexOf('.') + 1;
      token = token.substring(0, at) + (token.charAt(at) == 'A' ? "B" : "A") + token.substring(at + 1);
    }
    int port = owner.equals("user") ? 18081 : owner.equals("content") ? 18082 : 8090;
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
        .timeout(Duration.ofSeconds(12)).header("Authorization", "Bearer " + token).GET().build();
    var response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build()
        .send(request, HttpResponse.BodyHandlers.ofString());
    System.out.println(Json.text(Map.of("status", response.statusCode(), "body", Json.map(response.body()))));
  }
}
