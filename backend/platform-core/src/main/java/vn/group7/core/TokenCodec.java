package vn.group7.core;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;
import java.nio.file.*;
import java.security.*;
import java.security.interfaces.*;
import java.security.spec.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class TokenCodec {
  private final Path root;
  private final String self;
  private static final Set<String> SERVICES =
      Set.of("api", "user", "content", "assessment", "progress");

  public TokenCodec(
      @Value("${platform.keys}") String keys, @Value("${platform.service}") String service) {
    root = Path.of(keys);
    self = service;
  }

  private byte[] pem(String name) throws Exception {
    return Base64.getMimeDecoder()
        .decode(Files.readString(root.resolve(name)).replaceAll("-----[^-]+-----", ""));
  }

  public String sign(String kind, String audience, Map<String, Object> values, Instant expiry) {
    try {
      String issuer =
          kind.equals("user")
              ? "group7-user"
              : kind.equals("principal") ? "group7-gateway" : "group7-service:" + self;
      var b =
          new JWTClaimsSet.Builder()
              .issuer(issuer)
              .audience(audience)
              .issueTime(Date.from(Instant.now()))
              .expirationTime(Date.from(expiry))
              .jwtID(UUID.randomUUID().toString());
      values.forEach(b::claim);
      if (kind.equals("service")) b.subject(self);
      var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), b.build());
      var key =
          (RSAPrivateKey)
              KeyFactory.getInstance("RSA")
                  .generatePrivate(new PKCS8EncodedKeySpec(pem(self + ".private.pem")));
      jwt.sign(new RSASSASigner(key));
      return jwt.serialize();
    } catch (Exception e) {
      throw ApiError.dependency();
    }
  }

  public JWTClaimsSet verify(String token, String kind, String audience) {
    try {
      var jwt = SignedJWT.parse(token);
      var c = jwt.getJWTClaimsSet();
      String key = kind.equals("user") ? "user" : kind.equals("principal") ? "api" : c.getSubject();
      if (!SERVICES.contains(key)) throw new Exception();
      String iss =
          kind.equals("user")
              ? "group7-user"
              : kind.equals("principal") ? "group7-gateway" : "group7-service:" + key;
      if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())
          || !iss.equals(c.getIssuer())
          || !c.getAudience().contains(audience)
          || c.getExpirationTime() == null
          || !c.getExpirationTime().toInstant().isAfter(Instant.now())
          || c.getIssueTime() == null
          || c.getIssueTime().toInstant().isAfter(Instant.now().plusSeconds(5))
          || c.getJWTID() == null) throw new Exception();
      var pub =
          (RSAPublicKey)
              KeyFactory.getInstance("RSA")
                  .generatePublic(new X509EncodedKeySpec(pem(key + ".public.pem")));
      if (!jwt.verify(new RSASSAVerifier(pub))) throw new Exception();
      Input.id(c.getJWTID());
      return c;
    } catch (Exception e) {
      throw new ApiError(401, "SESSION_INVALID", "Phiên không hợp lệ");
    }
  }

  public String service(String audience) {
    return sign("service", audience, Map.of(), Instant.now().plusSeconds(60));
  }

  public static String bearer(String h) {
    if (h == null || !h.startsWith("Bearer "))
      throw new ApiError(401, "SESSION_INVALID", "Thiếu service identity");
    return h.substring(7);
  }

  public static String string(JWTClaimsSet c, String key) {
    try {
      return c.getStringClaim(key);
    } catch (Exception e) {
      throw new ApiError(401, "SESSION_INVALID", "Claims không hợp lệ");
    }
  }
}
