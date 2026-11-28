package vn.group7.assessment;

/** Transport boundary; implementations must wait for broker acknowledgement before returning. */
@FunctionalInterface
public interface EventPublisher {
  void publish(String topic, String key, String payload) throws Exception;
}
