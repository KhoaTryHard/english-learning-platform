package vn.group7.assessment;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.group7.core.Errors;

/** HTTP advice regression for failure before a transactional method obtains its connection. */
class TransactionUnavailableHttpTest {
  static final String PATH = "/internal/v1/lessons/10000000-0000-4000-8000-000000000001/exercise-readiness";

  @RestController
  static class FailingReadiness {
    final RuntimeException failure;

    FailingReadiness(RuntimeException failure) {
      this.failure = failure;
    }

    @GetMapping(PATH)
    String readiness() {
      throw failure;
    }
  }

  String expect(RuntimeException failure, int status, String code) throws Exception {
    return MockMvcBuilders.standaloneSetup(new FailingReadiness(failure))
        .setControllerAdvice(new Errors()).build().perform(get(PATH))
        .andExpect(status().is(status)).andExpect(jsonPath("$.code").value(code))
        .andReturn().getResponse().getContentAsString();
  }

  @Test void transactionConnectionFailureReturnsSanitized503() throws Exception {
    String detail = "jdbc:postgresql://private-db:5432/assessment secret-connection-detail";
    String body = expect(new CannotCreateTransactionException("Could not open JDBC Connection",
        new SQLException(detail, "08001")), 503, "DEPENDENCY_UNAVAILABLE");
    assertFalse(body.contains("private-db"));
    assertFalse(body.contains("secret-connection-detail"));
    assertFalse(body.contains("SQLException"));
  }

  @Test void queryConnectionFailureStillReturns503() throws Exception {
    expect(new DataAccessResourceFailureException("private query detail"), 503, "DEPENDENCY_UNAVAILABLE");
  }

  @Test void unrelatedRollbackRemainsInternalError() throws Exception {
    expect(new UnexpectedRollbackException("private business detail"), 500, "INTERNAL_ERROR");
  }

  @Test void integrityConflictRemains409() throws Exception {
    expect(new DataIntegrityViolationException("private constraint detail"), 409, "RESOURCE_CONFLICT");
  }
}
