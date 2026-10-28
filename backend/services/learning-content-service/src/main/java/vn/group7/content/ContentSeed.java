package vn.group7.content;

import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;
import vn.group7.core.*;

@Component
public class ContentSeed implements CommandLineRunner {
  private final Db db;
  private final ContentStore store;
  private final String mode, file;
  private final ConfigurableApplicationContext ctx;

  public ContentSeed(
      Db d,
      ContentStore s,
      @Value("${platform.mode}") String m,
      @Value("${SEED_FILE:}") String f,
      ConfigurableApplicationContext c) {
    db = d;
    store = s;
    mode = m;
    file = f;
    ctx = c;
  }

  public void run(String... args) throws Exception {
    if (!mode.equals("seed")) return;
    var data = Json.map(Files.readString(Path.of(file)));
    db.tx(
        () -> {
          for (Object item : (List<?>) data.getOrDefault("courses", List.of())) {
            var b = Json.obj(item);
            UUID id = Input.id((String) b.get("courseId"));
            String name = Input.text(b.get("name"), 200, false);
            db.run(
                "INSERT INTO course(course_id,name,name_normalized) VALUES(?,?,?) ON"
                    + " CONFLICT(course_id) DO NOTHING",
                id,
                name,
                Input.norm(name));
          }
          for (Object item : (List<?>) data.getOrDefault("levels", List.of())) {
            var b = Json.obj(item);
            UUID id = Input.id((String) b.get("levelId")),
                parent = Input.id((String) b.get("courseId"));
            String name = Input.text(b.get("name"), 200, false);
            db.run(
                "INSERT INTO level(level_id,course_id,name,name_normalized) VALUES(?,?,?,?) ON"
                    + " CONFLICT(level_id) DO NOTHING",
                id,
                parent,
                name,
                Input.norm(name));
          }
          return null;
        });
    org.springframework.boot.SpringApplication.exit(ctx);
  }
}
