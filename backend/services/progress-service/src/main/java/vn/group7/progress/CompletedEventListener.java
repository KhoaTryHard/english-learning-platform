package vn.group7.progress;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "progress.kafka.enabled", havingValue = "true")
public class CompletedEventListener {
  private final ProgressService service;

  public CompletedEventListener(ProgressService service) {
    this.service = service;
  }

  @KafkaListener(
      topics = "assessment.completed.v1",
      containerFactory = "completedEventContainerFactory")
  public void receive(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
    service.consume(
        record.value(), record.key()); // separate proxied bean commits its DB transaction here
    acknowledgment.acknowledge(); // no ACK when validation or DB commit fails
  }
}
