package vn.group7.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"vn.group7.content", "vn.group7.core"})
public class ContentApplication {
  public static void main(String[] args) {
    SpringApplication.run(ContentApplication.class, args);
  }
}
