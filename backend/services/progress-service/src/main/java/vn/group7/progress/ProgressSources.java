package vn.group7.progress;

import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.group7.core.*;

@Component
public class ProgressSources {
  private final OwnerClient client;
  private final String content, user;

  public ProgressSources(
      OwnerClient client,
      @Value("${platform.content-url}") String content,
      @Value("${platform.user-url}") String user) {
    this.client = client;
    this.content = content;
    this.user = user;
  }

  public int totalLessons(UUID course) {
    var value = client.get(content, "/internal/v1/courses/" + course + "/context", "content");
    try {
      if (!course.equals(Input.id(value.get("courseId").toString()))) throw ApiError.dependency();
      var number = value.get("totalLessons");
      if (!(number instanceof Number n)) throw ApiError.dependency();
      int total = new java.math.BigDecimal(n.toString()).intValueExact();
      if (total < 0) throw ApiError.dependency();
      return total;
    } catch (Exception e) {
      throw ApiError.dependency();
    }
  }

  public Map<String, Object> setup(UUID learner) {
    var value = client.get(user, "/internal/v1/users/" + learner + "/learning-setup", "user");
    try {
      if (!value.keySet().containsAll(Set.of("courseId", "levelId", "updatedAt")))
        throw ApiError.dependency();
      Object course = value.get("courseId"),
          level = value.get("levelId"),
          updated = value.get("updatedAt");
      if ((course == null) != (level == null)) throw ApiError.dependency();
      if (course != null) {
        course = Input.id(course.toString()).toString();
        level = Input.id(level.toString()).toString();
      }
      if (updated != null) updated = Instant.parse(updated.toString()).toString();
      return Json.nullable("courseId", course, "levelId", level, "updatedAt", updated);
    } catch (Exception e) {
      throw ApiError.dependency();
    }
  }
}
