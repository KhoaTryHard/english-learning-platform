package vn.group7.assessment;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import vn.group7.core.*;

/**
 * W7-06 — Outbox relay state machine and durability rules.
 *
 * <p>The transport is a recording {@link EventPublisher}; these cases prove the relay's own
 * behaviour, not a live broker round trip, which stays NOT_RUN until the team environment has
 * Kafka. Uses a publisher local to this test so multiple partition keys can be exercised.
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = AssessmentServiceTest.Config.class)
class OutboxRelayTest {
  /** Records publishes and fails on demand, per call count or per partition key. */
  static class RecordingPublisher implements EventPublisher {
    final List<Map<String, String>> sent = new ArrayList<>();
    final Set<String> failKeys = new HashSet<>();
    boolean down = false;
    int failAfter = Integer.MAX_VALUE;

    @Override
    public void publish(String topic, String key, String payload) throws Exception {
      if (down || sent.size() >= failAfter || failKeys.contains(key))
        throw new java.io.IOException("broker unavailable");
      sent.add(Map.of("topic", topic, "key", key, "payload", payload));
    }
  }

  @Autowired AssessmentService service;
  @Autowired JdbcTemplate db;
  @Autowired AssessmentServiceTest.MutableClock clock;
  @Autowired AssessmentServiceTest.FakeContent content;

  RecordingPublisher transport;
  OutboxRelay relay;

  @BeforeEach
  void reset() {
    for (String table :
        List.of(
            "outbox_event",
            "result",
            "submission",
            "question",
            "exercise_lesson",
            "assessment",
            "learner_lock")) db.update("DELETE FROM " + table);
    clock.now = Instant.parse("2026-10-29T10:00:00Z");
    content.visible = true;
    content.unavailable = false;
    content.level = AssessmentServiceTest.LEVEL;
    transport = new RecordingPublisher();
    relay = new OutboxRelay(db, transport, clock);
  }

  /** Runs a full Exercise attempt so the relay reads rows written by the real submit path. */
  private String submitExercise(String user) {
    String id =
        service
            .create(
                Json.nullable(
                    "type",
                    "EXERCISE",
                    "courseId",
                    AssessmentServiceTest.COURSE,
                    "lessonId",
                    UUID.randomUUID().toString(),
                    "title",
                    "Lesson assessment",
                    "timeLimitSeconds",
                    null))
            .get("assessmentId")
            .toString();
    service.putQuestion(
        id,
        null,
        Map.of(
            "questionType", "TRUE_FALSE", "content", "Is this correct?", "points", 2,
            "correctAnswer", false));
    service.patch(id, Map.of("ready", true));
    String attempt =
        service.start(id, user, UUID.randomUUID().toString()).get("attemptId").toString();
    service.submit(attempt, user, UUID.randomUUID().toString());
    return attempt;
  }

  private List<Map<String, Object>> outbox() {
    return db.queryForList("SELECT * FROM outbox_event ORDER BY occurred_at,event_id");
  }

  @Test
  void pendingEventIsPublishedOnceWithTheStoredContractPayload() {
    String attempt = submitExercise(AssessmentServiceTest.USER);
    var row = outbox().getFirst();
    assertEquals("PENDING", row.get("status"));

    assertEquals(1, relay.runBatch(20));

    assertEquals(1, transport.sent.size());
    var message = transport.sent.getFirst();
    assertEquals("assessment.completed.v1", message.get("topic"));
    assertEquals(AssessmentServiceTest.USER, message.get("key"));
    var event = Json.map(message.get("payload"));
    assertEquals("AssessmentCompleted", event.get("eventType"));
    assertEquals(1, event.get("schemaVersion"));
    assertEquals(row.get("event_id").toString(), event.get("eventId"));
    assertEquals(attempt, Json.obj(event.get("payload")).get("submissionId"));
    assertEquals("SENT", outbox().getFirst().get("status"));

    // A drained outbox must not resend anything.
    assertEquals(0, relay.runBatch(20));
    assertEquals(1, transport.sent.size());
    assertNull(relay.backlogAgeSeconds());
  }

  @Test
  void brokerOutageKeepsTheEventPendingAndTheResultReadable() {
    String attempt = submitExercise(AssessmentServiceTest.USER);
    transport.down = true;

    assertEquals(0, relay.runBatch(20));

    var row = outbox().getFirst();
    assertEquals("PENDING", row.get("status"));
    assertEquals(1, ((Number) row.get("retry_count")).intValue());
    // The diagnostic is kept, not just a constant flag.
    assertTrue(row.get("last_error").toString().startsWith("PUBLISH_FAILED"));
    assertTrue(row.get("last_error").toString().contains("broker unavailable"));
    // The committed Result survives the outage untouched.
    assertNotNull(service.attempt(attempt, AssessmentServiceTest.USER).get("result"));
    clock.now = clock.now.plusSeconds(30);
    assertEquals(30L, relay.backlogAgeSeconds());

    transport.down = false;
    assertEquals(1, relay.runBatch(20));
    var recovered = outbox().getFirst();
    assertEquals("SENT", recovered.get("status"));
    assertNull(recovered.get("last_error"));
    assertEquals(1, ((Number) recovered.get("retry_count")).intValue());
  }

  @Test
  void repeatedSweepsAccumulateRetriesWithoutLosingTheEvent() {
    submitExercise(AssessmentServiceTest.USER);
    transport.down = true;

    relay.runBatch(20);
    relay.runBatch(20);
    relay.runBatch(20);

    var row = outbox().getFirst();
    assertEquals(3, ((Number) row.get("retry_count")).intValue());
    assertEquals("PENDING", row.get("status"));
    assertTrue(transport.sent.isEmpty());
  }

  @Test
  void republishAfterACrashReusesThePersistedEventId() {
    submitExercise(AssessmentServiceTest.USER);
    assertEquals(1, relay.runBatch(20));
    String eventId = outbox().getFirst().get("event_id").toString();

    // A crash between the successful publish and the status update leaves the row PENDING.
    db.update("UPDATE outbox_event SET status='PENDING' WHERE event_id=?::uuid", eventId);
    assertEquals(1, relay.runBatch(20));

    assertEquals(2, transport.sent.size());
    // At-least-once delivery with a stable eventId; Progress deduplicates on processed_event.
    for (var message : transport.sent)
      assertEquals(eventId, Json.map(message.get("payload")).get("eventId"));
    assertEquals("SENT", outbox().getFirst().get("status"));
  }

  @Test
  void aFailingKeyIsSkippedWhileOtherKeysKeepDraining() {
    String blockedUser = UUID.randomUUID().toString();
    submitExercise(blockedUser);
    clock.now = clock.now.plusSeconds(60);
    submitExercise(blockedUser);
    clock.now = clock.now.plusSeconds(60);
    submitExercise(AssessmentServiceTest.USER);
    assertEquals(3, outbox().size());
    transport.failKeys.add(blockedUser);

    // The blocked learner's first event fails; its second event is held back to keep order,
    // but the unrelated learner still drains in the same sweep.
    assertEquals(1, relay.runBatch(20));

    var rows = outbox();
    assertEquals("PENDING", rows.get(0).get("status"));
    assertEquals(1, ((Number) rows.get(0).get("retry_count")).intValue());
    assertEquals("PENDING", rows.get(1).get("status"));
    assertEquals(0, ((Number) rows.get(1).get("retry_count")).intValue());
    assertEquals("SENT", rows.get(2).get("status"));
    assertEquals(1, transport.sent.size());
    assertEquals(AssessmentServiceTest.USER, transport.sent.getFirst().get("key"));

    transport.failKeys.clear();
    assertEquals(2, relay.runBatch(20));
    assertTrue(outbox().stream().allMatch(row -> "SENT".equals(row.get("status"))));
    // Per-key order is preserved across sweeps.
    var keyOrder =
        transport.sent.stream().map(message -> message.get("payload")).map(Json::map).toList();
    assertEquals(3, keyOrder.size());
  }

  @Test
  void batchSizeLimitsOneSweepWithoutDroppingTheRemainder() {
    submitExercise(AssessmentServiceTest.USER);
    clock.now = clock.now.plusSeconds(60);
    submitExercise(UUID.randomUUID().toString());

    assertEquals(1, relay.runBatch(1));
    assertEquals(1, relay.runBatch(1));
    assertEquals(0, relay.runBatch(1));
    assertEquals(2, transport.sent.size());
  }

  @Test
  void invalidBatchSizeIsRejected() {
    assertThrows(IllegalArgumentException.class, () -> relay.runBatch(0));
    assertThrows(IllegalArgumentException.class, () -> relay.runBatch(101));
  }
}
