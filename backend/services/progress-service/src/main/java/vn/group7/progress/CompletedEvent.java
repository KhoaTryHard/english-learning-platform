package vn.group7.progress;

import com.networknt.schema.*;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import vn.group7.core.*;

/** Validates untrusted broker input before any database write. */
public record CompletedEvent(
    UUID eventId,
    UUID userId,
    UUID assessmentId,
    String type,
    UUID submissionId,
    UUID resultId,
    UUID lessonId,
    UUID courseId,
    UUID levelId,
    BigDecimal earned,
    BigDecimal max,
    Instant completedAt,
    String payloadHash) {
  private static final JsonSchema SCHEMA;

  static {
    try (var stream =
        CompletedEvent.class.getResourceAsStream("/contracts/AssessmentCompleted_v1.schema.json")) {
      SCHEMA =
          JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
              .getSchema(Json.M.readTree(stream));
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  public static CompletedEvent parse(String raw, String kafkaKey) {
    try {
      var tree = Json.M.readTree(raw);
      if (!SCHEMA.validate(tree).isEmpty())
        throw new IllegalArgumentException("Event schema invalid");
      var event = Json.map(raw);
      var p = Json.obj(event.get("payload"));
      UUID eid = id(event.get("eventId")), user = id(p.get("userId"));
      id(event.get("correlationId"));
      Instant.parse(event.get("occurredAt").toString());
      if (!user.toString().equals(kafkaKey))
        throw new IllegalArgumentException("Kafka key must equal userId");
      var earned = new BigDecimal(p.get("earnedPoints").toString());
      var max = new BigDecimal(p.get("maxPoints").toString());
      if (earned.signum() < 0 || max.signum() <= 0 || earned.compareTo(max) > 0)
        throw new IllegalArgumentException("Invalid score range");
      var normalized = new TreeMap<String, Object>(p);
      normalized.put("earnedPoints", earned.stripTrailingZeros().toPlainString());
      normalized.put("maxPoints", max.stripTrailingZeros().toPlainString());
      Instant completed = Instant.parse(p.get("completedAt").toString());
      normalized.put("completedAt", completed.toString());
      for (String field :
          List.of(
              "userId", "assessmentId", "submissionId", "resultId", "courseId", "levelIdAtStart"))
        normalized.put(field, id(p.get(field)).toString());
      UUID lesson = p.get("lessonId") == null ? null : id(p.get("lessonId"));
      normalized.put("lessonId", lesson == null ? null : lesson.toString());
      String hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(
                          Json.text(normalized).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
      return new CompletedEvent(
          eid,
          user,
          id(p.get("assessmentId")),
          p.get("assessmentType").toString(),
          id(p.get("submissionId")),
          id(p.get("resultId")),
          lesson,
          id(p.get("courseId")),
          id(p.get("levelIdAtStart")),
          earned,
          max,
          completed,
          hash);
    } catch (Exception failure) {
      throw new IllegalArgumentException("Invalid AssessmentCompleted event", failure);
    }
  }

  private static UUID id(Object value) {
    return Input.id(value == null ? null : value.toString());
  }
}
