package vn.group7.user;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.nimbusds.jwt.JWTClaimsSet;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.group7.core.*;

/** Real MVC/controller/advice boundary; account, token and session storage are mocks. */
class SessionValidationTest {
  JdbcIndexedSessionRepository sessions;
  MockMvc mvc;
  String jti;

  @BeforeEach void setup() {
    Db db = mock(Db.class);
    TokenCodec codec = mock(TokenCodec.class);
    sessions = mock(JdbcIndexedSessionRepository.class, RETURNS_DEEP_STUBS);
    jti = UUID.randomUUID().toString();
    var claims = new JWTClaimsSet.Builder().subject(UUID.randomUUID().toString())
        .jwtID(jti).claim("role", "LEARNER")
        .expirationTime(Date.from(Instant.now().plusSeconds(60))).build();
    when(codec.verify(anyString(), anyString(), anyString())).thenReturn(claims);
    when(db.one(anyString(), any())).thenReturn(Map.of("status", "ACTIVE", "role", "LEARNER"));
    var controller = new UserController(db, codec, null, sessions, null,
        "http://unused", "http://localhost:5173", false, 30);
    mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new Errors()).build();
  }

  org.springframework.mock.web.MockHttpServletResponse validate(String csrf) throws Exception {
    return mvc.perform(post("/internal/v1/sessions/validate").contentType("application/json")
        .content(Json.text(Map.of("token", "private-test-token", "requestMethod", "PATCH",
            "csrfSessionId", "session", "csrfToken", csrf)))).andReturn().getResponse();
  }

  @Test void sessionStoreOutageIs503WithoutLeakingDetails() throws Exception {
    when(sessions.findById("session")).thenThrow(
        new DataAccessResourceFailureException("private-database-detail"));
    var response = validate("csrf");
    assertEquals(503, response.getStatus());
    assertEquals("DEPENDENCY_UNAVAILABLE", Json.map(response.getContentAsString()).get("code"));
    assertFalse(response.getContentAsString().contains("private-database-detail"));
    assertFalse(response.getContentAsString().contains("private-test-token"));
  }

  @Test void missingSessionRemains403() throws Exception {
    when(sessions.findById("session")).thenReturn(null);
    var response = validate("csrf");
    assertEquals(403, response.getStatus());
    assertEquals("CSRF_INVALID", Json.map(response.getContentAsString()).get("code"));
  }

  @Test void wrongCsrfRemains403AndValidSessionSucceeds() throws Exception {
    org.springframework.session.Session session = sessions.createSession();
    when(session.getAttribute("jti")).thenReturn(jti);
    when(session.getAttribute("jwtExpiry")).thenReturn(Instant.now().plusSeconds(60).getEpochSecond());
    when(session.getAttribute("csrf")).thenReturn("correct-csrf");
    doReturn(session).when(sessions).findById("session");
    assertEquals(403, validate("wrong-csrf").getStatus());
    assertEquals(200, validate("correct-csrf").getStatus());
  }
}
