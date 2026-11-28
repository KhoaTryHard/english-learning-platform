package vn.group7.assessment;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(
    properties = {
      "spring.datasource.url=jdbc:h2:mem:boot;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "spring.datasource.username=sa",
      "spring.datasource.password=",
      "assessment.expiry-delay-ms=3600000"
    })
class AssessmentApplicationTest {
  @Autowired JdbcTemplate db;

  @Test
  void bootsWithFlywayAndScheduledFinalizer() {
    assertEquals(0, db.queryForObject("SELECT count(*) FROM outbox_event", Integer.class));
    assertEquals(
        1,
        db.queryForObject(
            "SELECT count(*) FROM flyway_schema_history WHERE success=TRUE AND version='1'",
            Integer.class));
  }
}
