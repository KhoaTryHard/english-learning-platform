package vn.group7.progress;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = {"vn.group7.progress", "vn.group7.core"})
public class ProgressApplication {
  public static void main(String[] args) {
    SpringApplication.run(ProgressApplication.class, args);
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
