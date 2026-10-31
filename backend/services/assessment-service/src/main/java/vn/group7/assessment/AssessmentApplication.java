package vn.group7.assessment;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"vn.group7.assessment", "vn.group7.core"})
@EnableScheduling
public class AssessmentApplication {
  public static void main(String[] args) {
    SpringApplication.run(AssessmentApplication.class, args);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
