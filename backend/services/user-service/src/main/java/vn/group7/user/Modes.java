package vn.group7.user;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class Modes implements CommandLineRunner {
  private final String mode;
  private final ConfigurableApplicationContext ctx;

  public Modes(@Value("${platform.mode}") String m, ConfigurableApplicationContext c) {
    mode = m;
    ctx = c;
  }

  public void run(String... args) {
    if (mode.equals("migrate")) org.springframework.boot.SpringApplication.exit(ctx);
  }
}
