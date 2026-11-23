package vn.group7.assessment;

import java.time.Clock;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@ConditionalOnProperty(name = "assessment.kafka.enabled", havingValue = "true")
public class OutboxKafkaConfiguration {
  @Bean
  EventPublisher kafkaEventPublisher(
      KafkaTemplate<String, String> template,
      @Value("${assessment.kafka.send-timeout-seconds:10}") long sendTimeoutSeconds) {
    // Blocks until the broker acknowledges, so a failed send is never reported as published.
    return (topic, key, payload) ->
        template.send(topic, key, payload).get(sendTimeoutSeconds, TimeUnit.SECONDS);
  }

  @Bean
  OutboxRelay outboxRelay(JdbcTemplate db, EventPublisher publisher, Clock clock) {
    return new OutboxRelay(db, publisher, clock);
  }

  @Bean
  RelayJob relayJob(
      OutboxRelay relay, @Value("${assessment.kafka.relay-batch-size:20}") int batchSize) {
    return new RelayJob(relay, batchSize);
  }

  static class RelayJob {
    private final OutboxRelay relay;
    private final int batchSize;

    RelayJob(OutboxRelay relay, int batchSize) {
      this.relay = relay;
      this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${assessment.kafka.relay-delay-ms:1000}")
    void run() {
      try {
        relay.runBatch(batchSize);
      } catch (RuntimeException ignored) {
        // A failed sweep leaves rows PENDING; the next scheduled sweep retries them.
      }
    }
  }
}
