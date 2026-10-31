package vn.group7.assessment;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.group7.core.*;

@Component
public class ContentContext {
  private final OwnerClient client;
  private final String userUrl, contentUrl;

  public ContentContext(
      OwnerClient client,
      @Value("${platform.user-url}") String user,
      @Value("${platform.content-url}") String content) {
    this.client = client;
    userUrl = user;
    contentUrl = content;
  }

  public Map<String, Object> context(String course, String lesson) {
    var value =
        client.get(
            contentUrl,
            lesson == null
                ? "/internal/v1/courses/" + course + "/context"
                : "/internal/v1/lessons/" + lesson + "/context",
            "content");
    try {
      if (!course.equals(Validation.uuid(value.get("courseId"))))
        throw new ApiError(409, "RESOURCE_CONFLICT", "Course/lesson mismatch");
      if (lesson != null && !lesson.equals(Validation.uuid(value.get("lessonId"))))
        throw ApiError.dependency();
      Validation.bool(value.get(lesson == null ? "published" : "visibleToLearner"));
      return value;
    } catch (ApiError e) {
      if (e.status == 400) throw ApiError.dependency();
      throw e;
    }
  }

  public String level(String user, String course) {
    var setup = client.get(userUrl, "/internal/v1/users/" + user + "/learning-setup", "user");
    try {
      if (!course.equals(Validation.nullableId(setup.get("courseId")))
          || setup.get("levelId") == null)
        throw new ApiError(409, "LEARNING_SETUP_MISMATCH", "Choose matching Course/Level first");
      String level = Validation.uuid(setup.get("levelId"));
      var c = client.get(contentUrl, "/internal/v1/courses/" + course + "/context", "content");
      if (!course.equals(Validation.uuid(c.get("courseId")))) throw ApiError.dependency();
      boolean valid =
          Validation.list(c.get("levels")).stream()
              .anyMatch(x -> level.equals(Validation.uuid(Validation.object(x).get("levelId"))));
      if (!valid) throw new ApiError(409, "LEARNING_SETUP_MISMATCH", "Level outside Course");
      return level;
    } catch (ApiError e) {
      if (e.status == 400) throw ApiError.dependency();
      throw e;
    }
  }
}
