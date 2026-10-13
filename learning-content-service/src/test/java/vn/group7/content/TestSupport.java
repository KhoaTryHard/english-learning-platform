package vn.group7.content;

import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.time.*;
import java.util.*;
import vn.group7.core.*;

final class TestSupport {
  static final Path KEYS = keys();

  static Path keys() {
    try {
      Path p = Files.createTempDirectory("group7-test-keys");
      for (String s : List.of("api", "user", "content", "assessment", "progress")) {
        var g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        var k = g.generateKeyPair();
        Files.writeString(
            p.resolve(s + ".private.pem"), pem("PRIVATE KEY", k.getPrivate().getEncoded()));
        Files.writeString(
            p.resolve(s + ".public.pem"), pem("PUBLIC KEY", k.getPublic().getEncoded()));
      }
      return p;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  static String pem(String type, byte[] b) {
    return "-----BEGIN "
        + type
        + "-----\n"
        + Base64.getMimeEncoder(64, new byte[] {10}).encodeToString(b)
        + "\n-----END "
        + type
        + "-----\n";
  }

  static TokenCodec caller(String s) {
    return new TokenCodec(KEYS.toString(), s);
  }

  static String assertion(String owner, String method, String path, UUID user, String role) {
    return caller("api")
        .sign(
            "principal",
            owner,
            Map.of(
                "sub",
                user.toString(),
                "role",
                role,
                "sessionJti",
                UUID.randomUUID().toString(),
                "method",
                method,
                "path",
                path),
            Instant.now().plusSeconds(15));
  }
}
