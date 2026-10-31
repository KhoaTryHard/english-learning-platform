package vn.group7.progress;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import vn.group7.core.Json;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ProgressServiceTest.Config.class)
class CompletedEventListenerTest {
  @Autowired ProgressService service;
  @Autowired JdbcTemplate db;

  @BeforeEach
  void clear() {
    for (String table :
        List.of(
            "processed_event",
            "learning_history",
            "lesson_completion",
            "course_progress",
            "learner_lock")) db.update("DELETE FROM " + table);
  }

  @Test
  void acknowledgesOnlyAfterCommittedProjection() {
    var listener = new CompletedEventListener(service);
    var event = ProgressServiceTest.event("EXERCISE", ProgressServiceTest.LESSON);
    var record =
        new ConsumerRecord<String, String>(
            "assessment.completed.v1", 0, 0, ProgressServiceTest.USER.toString(), Json.text(event));
    var acknowledged = new AtomicBoolean();
    listener.receive(
        record,
        () -> {
          assertEquals(
              1, db.queryForObject("SELECT count(*) FROM learning_history", Integer.class));
          acknowledged.set(true);
        });
    assertTrue(acknowledged.get());
  }

  @Test
  void failedEventNeverAcknowledges() {
    var listener = new CompletedEventListener(service);
    var ack = new AtomicBoolean();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            listener.receive(
                new ConsumerRecord<String, String>("assessment.completed.v1", 0, 0, "wrong", "{}"),
                () -> ack.set(true)));
    assertFalse(ack.get());
  }

  @Test
  void databaseFailureDoesNotCommitOffset() {
    var listener = new CompletedEventListener(service);
    var event = ProgressServiceTest.event("EXERCISE", ProgressServiceTest.LESSON);
    var ack = new AtomicBoolean();
    db.execute(
        "ALTER TABLE course_progress ADD CONSTRAINT reject_listener CHECK(completed_lessons=999)");
    try {
      assertThrows(
          Exception.class,
          () ->
              listener.receive(
                  new ConsumerRecord<String, String>(
                      "assessment.completed.v1",
                      0,
                      0,
                      ProgressServiceTest.USER.toString(),
                      Json.text(event)),
                  () -> ack.set(true)));
      assertFalse(ack.get());
      assertEquals(0, db.queryForObject("SELECT count(*) FROM processed_event", Integer.class));
    } finally {
      db.execute("ALTER TABLE course_progress DROP CONSTRAINT reject_listener");
    }
  }
}
