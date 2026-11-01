package vn.group7.content;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.sql.SQLTransientConnectionException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.web.bind.annotation.*;
import vn.group7.core.*;

/** MVC + actual Db transaction adapter; JDBC/transaction manager are fault doubles. */
class DbTransactionFailureTest {
  PlatformTransactionManager manager;
  JdbcTemplate jdbc;
  AtomicInteger writes;
  MockMvc mvc;

  @RestController
  static class Endpoint {
    final Db db;
    final AtomicInteger writes;

    Endpoint(Db db, AtomicInteger writes) {
      this.db = db;
      this.writes = writes;
    }

    @PostMapping("/transaction-test")
    Object create() {
      return db.tx(() -> {
        writes.incrementAndGet();
        db.run("INSERT TEST");
        return java.util.Map.of("created", true);
      });
    }
  }

  @BeforeEach
  void setup() {
    manager = mock(PlatformTransactionManager.class);
    jdbc = mock(JdbcTemplate.class);
    writes = new AtomicInteger();
    when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
    mvc = MockMvcBuilders.standaloneSetup(new Endpoint(new Db(jdbc, manager), writes))
        .setControllerAdvice(new Errors()).build();
  }

  org.springframework.mock.web.MockHttpServletResponse call() throws Exception {
    return mvc.perform(post("/transaction-test")).andReturn().getResponse();
  }

  @Test
  void connectionFailureBeforeTransactionReturnsSanitized503WithoutWriting() throws Exception {
    when(manager.getTransaction(any())).thenThrow(new CannotCreateTransactionException(
        "private-jdbc-password", new SQLTransientConnectionException("private-host")));
    var response = call();
    assertEquals(503, response.getStatus());
    assertEquals("DEPENDENCY_UNAVAILABLE", Json.map(response.getContentAsString()).get("code"));
    assertFalse(response.getContentAsString().contains("private-"));
    assertEquals(0, writes.get());
    verifyNoInteractions(jdbc);
  }

  @Test
  void poolAcquisitionTimeoutReturns503AndNextRequestCanRecover() throws Exception {
    when(manager.getTransaction(any()))
        .thenThrow(new CannotCreateTransactionException("pool acquisition timed out"))
        .thenReturn(new SimpleTransactionStatus());
    assertEquals(503, call().getStatus());
    assertEquals(200, call().getStatus());
    assertEquals(1, writes.get());
  }

  @Test
  void unrelatedTransactionFailureRemains500() throws Exception {
    when(manager.getTransaction(any())).thenThrow(new UnexpectedRollbackException("private-detail"));
    var response = call();
    assertEquals(500, response.getStatus());
    assertEquals("INTERNAL_ERROR", Json.map(response.getContentAsString()).get("code"));
    assertFalse(response.getContentAsString().contains("private-detail"));
    assertEquals(0, writes.get());
  }

  @Test
  void duplicateWriteRemains409() throws Exception {
    when(jdbc.update("INSERT TEST", new Object[0])).thenThrow(new DuplicateKeyException("private-key"));
    var response = call();
    assertEquals(409, response.getStatus());
    assertEquals("RESOURCE_CONFLICT", Json.map(response.getContentAsString()).get("code"));
    assertFalse(response.getContentAsString().contains("private-key"));
  }
}
