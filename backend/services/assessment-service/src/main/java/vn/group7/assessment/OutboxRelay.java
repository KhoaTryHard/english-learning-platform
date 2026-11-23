package vn.group7.assessment;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * W7-06 relay: publishes rows already committed by {@link AssessmentService} to the transport.
 *
 * <p>At-least-once: a replay after a publish or mark failure always reuses the stored eventId.
 *
 * <p>Deliberately <strong>not</strong> transactional. Result, outbox_event and the submission
 * status are committed together by submit; publishing happens afterwards, so a hanging broker
 * never holds a row lock or a database connection and never rolls back a stored Result. A crash
 * between a successful publish and the PENDING-to-SENT update republishes the same persisted
 * eventId, which Progress deduplicates on processed_event.
 *
 * <p>Because no row is locked, two relay instances may publish the same event twice. That is safe
 * under at-least-once delivery with consumer deduplication, and is preferred over holding broker
 * I/O inside a database transaction.
 */
public class OutboxRelay {
  private final JdbcTemplate db;
  private final EventPublisher publisher;
  private final Clock clock;

  public OutboxRelay(JdbcTemplate db, EventPublisher publisher) {
    this(db, publisher, Clock.systemUTC());
  }

  public OutboxRelay(JdbcTemplate db, EventPublisher publisher, Clock clock) {
    this.db = db;
    this.publisher = publisher;
    this.clock = clock;
  }

  /**
   * Publishes one batch of PENDING events in occurrence order.
   *
   * <p>When an event fails to publish, the remaining events of that same partition key are left
   * for the next sweep so per-key ordering is preserved, while other keys keep draining. A single
   * undeliverable event therefore cannot block every learner's events.
   *
   * @return the number of rows this sweep moved from PENDING to SENT
   */
  public int runBatch(int limit) {
    if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid outbox batch size");
    int sent = 0;
    var blocked = new HashSet<String>();
    for (var row :
        db.queryForList(
            "SELECT event_id,partition_key,topic,payload FROM outbox_event WHERE status='PENDING'"
                + " ORDER BY occurred_at,event_id LIMIT ?",
            limit)) {
      Object eventId = row.get("event_id");
      String key = row.get("partition_key").toString();
      if (blocked.contains(key)) continue;
      try {
        publisher.publish(row.get("topic").toString(), key, row.get("payload").toString());
      } catch (Exception failure) {
        if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
        recordFailure(eventId, failure);
        blocked.add(key);
        continue;
      }
      // Guarded update: an already-SENT row changes nothing and is never counted twice.
      sent +=
          db.update(
              "UPDATE outbox_event SET status='SENT',last_error=NULL WHERE event_id=? AND"
                  + " status='PENDING'",
              eventId);
    }
    return sent;
  }

  private void recordFailure(Object eventId, Exception failure) {
    String message = "PUBLISH_FAILED: " + failure.getClass().getSimpleName();
    if (failure.getMessage() != null) message += ": " + failure.getMessage();
    db.update(
        "UPDATE outbox_event SET retry_count=retry_count+1,last_error=? WHERE event_id=? AND"
            + " status='PENDING'",
        message.length() > 1000 ? message.substring(0, 1000) : message,
        eventId);
  }

  /** Age in seconds of the oldest unsent event, or null when the outbox is drained. */
  public Long backlogAgeSeconds() {
    var rows =
        db.queryForList("SELECT min(occurred_at) AS oldest FROM outbox_event WHERE status='PENDING'");
    Object oldest = rows.isEmpty() ? null : rows.getFirst().get("oldest");
    if (oldest == null) return null;
    var instant =
        oldest instanceof Timestamp t ? t.toInstant() : ((OffsetDateTime) oldest).toInstant();
    return clock.instant().getEpochSecond() - instant.getEpochSecond();
  }
}
