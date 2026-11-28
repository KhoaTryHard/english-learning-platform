package vn.group7.assessment;

import java.util.concurrent.TimeUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@ConditionalOnProperty(name = "assessment.kafka.enabled", havingValue = "true")
public class OutboxKafkaConfiguration {
  @Bean
  EventPublisher kafkaEventPublisher(KafkaTemplate<String, String> template) {
    return (topic, key, payload) -> template.send(topic, key, payload).get(10, TimeUnit.SECONDS);
  }

  @Bean
  OutboxRelay outboxRelay(JdbcTemplate db, EventPublisher publisher) {
    return new OutboxRelay(db, publisher);
  }

  @Bean
  RelayJob relayJob(OutboxRelay relay) {
    return new RelayJob(relay);
  }

  static class RelayJob {
    private final OutboxRelay relay;

    RelayJob(OutboxRelay relay) {
      this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${assessment.kafka.relay-delay-ms:1000}")
    void run() {
      relay.runBatch(20);
    }
  }
}
