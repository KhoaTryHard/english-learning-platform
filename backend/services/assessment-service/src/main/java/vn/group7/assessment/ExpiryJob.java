package vn.group7.assessment;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class ExpiryJob {
  private final AssessmentService service;

  ExpiryJob(AssessmentService service) {
    this.service = service;
  }

  @Scheduled(fixedDelayString = "${assessment.expiry-delay-ms}")
  void finalizeExpired() {
    service.expireDue();
  }
}
