package vn.group7.progress;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:progressboot;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "progress.kafka.enabled=false"
    })
class ProgressApplicationTest {
  @Autowired JdbcTemplate db;
  @Autowired ApplicationContext context;

  @Test
  void bootsWithoutBrokerAndMigratesSchema() {
    assertEquals(0, db.queryForObject("SELECT count(*) FROM processed_event", Integer.class));
    assertTrue(context.getBeansOfType(CompletedEventListener.class).isEmpty());
  }
}
