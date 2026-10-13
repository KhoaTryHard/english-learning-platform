package vn.group7.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import vn.group7.core.*;

@Component
public class AdminBootstrap implements CommandLineRunner {
  private final Db db;
  private final BCryptPasswordEncoder encoder;
  private final String mode, email, password;
  private final ConfigurableApplicationContext ctx;

  public AdminBootstrap(
      Db d,
      BCryptPasswordEncoder e,
      @Value("${platform.mode}") String m,
      @Value("${ADMIN_EMAIL:}") String em,
      @Value("${ADMIN_PASSWORD:}") String pw,
      ConfigurableApplicationContext c) {
    db = d;
    encoder = e;
    mode = m;
    email = em;
    password = pw;
    ctx = c;
  }

  public void run(String... args) {
    if (!mode.equals("bootstrap-admin")) return;
    Input.check("RegisterRequest", Json.nullable("email", email, "password", password));
    String em = UserController.email(email);
    var found = db.rows("SELECT role FROM app_user WHERE email_normalized=?", em);
    if (!found.isEmpty()) {
      if (!"ADMIN".equals(found.getFirst().get("role")))
        throw new IllegalStateException("Existing Learner cannot be escalated");
    } else {
      db.run(
          "INSERT INTO app_user(email_normalized,password_hash,role,status)"
              + " VALUES(?,?,'ADMIN','ACTIVE')",
          em,
          encoder.encode(UserController.password(password)));
    }
    org.springframework.boot.SpringApplication.exit(ctx);
  }
}
