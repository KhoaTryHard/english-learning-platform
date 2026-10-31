package vn.group7.assessment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** At-least-once: replay after publish/mark failure always reuses the stored eventId. */
public class OutboxRelay {
  private final JdbcTemplate db;
  private final EventPublisher publisher;

  public OutboxRelay(JdbcTemplate db, EventPublisher publisher) {
    this.db = db;
    this.publisher = publisher;
  }

  @Transactional
  public int runBatch(int limit) {
    if (limit < 1 || limit > 100) throw new IllegalArgumentException("Invalid outbox batch size");
    int sent = 0;
    for (var row :
        db.queryForList(
            "SELECT * FROM outbox_event WHERE status='PENDING' ORDER BY occurred_at,event_id LIMIT"
                + " ? FOR UPDATE SKIP LOCKED",
            limit)) {
      try {
        publisher.publish(
            row.get("topic").toString(),
            row.get("partition_key").toString(),
            row.get("payload").toString());
      } catch (Exception failure) {
        if (failure instanceof InterruptedException) Thread.currentThread().interrupt();
        db.update(
            "UPDATE outbox_event SET retry_count=retry_count+1,last_error='PUBLISH_FAILED' WHERE"
                + " event_id=?",
            row.get("event_id"));
        continue;
      }
      db.update(
          "UPDATE outbox_event SET status='SENT',last_error=NULL WHERE event_id=?",
          row.get("event_id"));
      sent++;
    }
    return sent;
  }
}
