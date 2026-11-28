package vn.group7.progress;

import java.time.Duration;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.*;

@Configuration
@ConditionalOnProperty(name = "progress.kafka.enabled", havingValue = "true")
public class KafkaConfiguration {
  @Bean
  NewTopic completedTopic() {
    return TopicBuilder.name("assessment.completed.v1").partitions(1).replicas(1).build();
  }

  @Bean
  NewTopic completedDeadLetterTopic() {
    return TopicBuilder.name("assessment.completed.v1.DLT").partitions(1).replicas(1).build();
  }

  @Bean
  ConcurrentKafkaListenerContainerFactory<String, String> completedEventContainerFactory(
      ConsumerFactory<String, String> consumers,
      KafkaTemplate<String, String> template,
      @org.springframework.beans.factory.annotation.Value(
              "${progress.kafka.listener-auto-startup:true}")
          boolean autoStartup) {
    var recoverer = new DeadLetterPublishingRecoverer(template);
    recoverer.setFailIfSendResultIsError(true);
    recoverer.setWaitForSendResultTimeout(Duration.ofSeconds(10));
    BackOff delays =
        () ->
            new BackOffExecution() {
              int next;
              final long[] milliseconds = {1000, 5000, 30000};

              public long nextBackOff() {
                return next < milliseconds.length ? milliseconds[next++] : STOP;
              }
            };
    var errors = new DefaultErrorHandler(recoverer, delays);
    errors.addNotRetryableExceptions(IllegalArgumentException.class);
    errors.setCommitRecovered(true);
    var factory = new ConcurrentKafkaListenerContainerFactory<String, String>();
    factory.setConsumerFactory(consumers);
    factory.setAutoStartup(autoStartup);
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
    factory.setCommonErrorHandler(errors);
    return factory;
  }
}
