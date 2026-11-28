package vn.group7.progress;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:progresskafka;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "progress.kafka.enabled=true",
      "progress.kafka.listener-auto-startup=false",
      "spring.kafka.admin.auto-create=false"
    })
class KafkaWiringTest {
  @Autowired CompletedEventListener listener;
  @Autowired KafkaListenerEndpointRegistry registry;
  @Autowired KafkaTemplate<?, ?> template;

  @Test
  void enabledAdapterWiresWithoutConnectingToBroker() {
    assertNotNull(listener);
    assertNotNull(template);
    assertEquals(1, registry.getListenerContainers().size());
    assertFalse(registry.getListenerContainers().iterator().next().isRunning());
  }
}
