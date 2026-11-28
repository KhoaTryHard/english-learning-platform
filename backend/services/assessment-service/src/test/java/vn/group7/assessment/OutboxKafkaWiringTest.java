package vn.group7.assessment;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:outboxkafka;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "assessment.kafka.enabled=true",
      "assessment.kafka.relay-delay-ms=3600000",
      "spring.kafka.admin.auto-create=false"
    })
class OutboxKafkaWiringTest {
  @Autowired OutboxRelay relay;
  @Autowired EventPublisher publisher;

  @Test
  void enabledTransportWiresAndEmptyBatchDoesNotPublish() {
    assertNotNull(publisher);
    assertEquals(0, relay.runBatch(1));
  }
}
