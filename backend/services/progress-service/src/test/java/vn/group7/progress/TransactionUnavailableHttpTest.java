package vn.group7.progress;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.group7.core.Errors;

/**
 * Same regression as the Assessment fix for #44, applied to Progress.
 *
 * <p>CannotCreateTransactionException does not extend DataAccessException, so a database that is
 * unreachable before a transactional read obtains its connection used to surface as 500. The
 * Statistics contract requires 503 for an unavailable mandatory dependency, and no connection
 * detail may leak to the client.
 */
class TransactionUnavailableHttpTest {
  static final String PATH = "/api/v1/admin/statistics";

  @RestController
  static class FailingStatistics {
    final RuntimeException failure;

    FailingStatistics(RuntimeException failure) {
      this.failure = failure;
    }

    @GetMapping(PATH)
    String statistics() {
      throw failure;
    }
  }

  private String expect(RuntimeException failure, int status, String code) throws Exception {
    return MockMvcBuilders.standaloneSetup(new FailingStatistics(failure))
        .setControllerAdvice(new Errors())
        .build()
        .perform(get(PATH))
        .andExpect(status().is(status))
        .andExpect(jsonPath("$.code").value(code))
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  void transactionConnectionFailureReturnsSanitized503() throws Exception {
    String detail = "jdbc:postgresql://private-db:5432/progress secret-connection-detail";
    String body =
        expect(
            new CannotCreateTransactionException(
                "Could not open JDBC Connection", new SQLException(detail, "08001")),
            503,
            "DEPENDENCY_UNAVAILABLE");
    assertFalse(body.contains("private-db"));
    assertFalse(body.contains("secret-connection-detail"));
    assertFalse(body.contains("SQLException"));
  }

  @Test
  void queryConnectionFailureStillReturns503() throws Exception {
    expect(
        new DataAccessResourceFailureException("private query detail"),
        503,
        "DEPENDENCY_UNAVAILABLE");
  }

  @Test
  void unrelatedRollbackRemainsInternalError() throws Exception {
    expect(new UnexpectedRollbackException("private business detail"), 500, "INTERNAL_ERROR");
  }

  @Test
  void integrityConflictRemains409() throws Exception {
    expect(
        new DataIntegrityViolationException("private constraint detail"), 409, "RESOURCE_CONFLICT");
  }
}
