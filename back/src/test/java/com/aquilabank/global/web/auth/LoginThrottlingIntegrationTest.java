package com.aquilabank.global.web.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenIssuer;
import com.aquilabank.global.security.LoginThrottleGuard;
import com.aquilabank.support.PostgresContainerTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.flyway.enabled=true",
      "management.health.db.enabled=true",
      "security.login-protection.max-failures=3",
      "security.login-protection.lock-seconds=1",
      "security.login-protection.reset-window-seconds=900",
      "security.login-throttling.max-tracked-ips=16",
      "security.login-throttling.ip.max-attempts=2",
      "security.login-throttling.ip.window-seconds=1",
      "security.login-throttling.global.max-attempts=4",
      "security.login-throttling.global.window-seconds=1"
    })
class LoginThrottlingIntegrationTest extends PostgresContainerTestSupport {

  private static final RequestClientMetadata WINDOWS_CHROME =
      new RequestClientMetadata(
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
              + "(KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36",
          "203.0.113.10, 10.0.0.1");

  @Autowired private WebApplicationContext context;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private LoginThrottleGuard loginThrottleGuard;

  @Autowired private InternalServiceTokenIssuer internalServiceTokenIssuer;

  private MockMvc mockMvc;
  private long userId;

  @BeforeEach
  void setUpDatabase() throws Exception {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    resetBankingTables(jdbcTemplate);
    loginThrottleGuard.clear();
    userId = bootstrapUser("alice", "Alice", "password123!");
  }

  @Test
  void throttlesRepeatedLoginBurstFromSameIpBeforeDomainLogin(CapturedOutput output)
      throws Exception {
    RequestClientMetadata sharedIp = clientMetadata("203.0.113.200");
    RequestClientMetadata otherIp = clientMetadata("203.0.113.201");

    loginExpectUnauthorized("alice", "wrong-password", "login-ip-throttle-001", sharedIp);
    loginExpectUnauthorized("alice", "wrong-password", "login-ip-throttle-002", sharedIp);

    LoginProtectionState throttledState = loadLoginProtectionState(userId);
    assertEquals(2, throttledState.failedLoginCount());
    assertNotNull(throttledState.lastLoginFailedAt());
    assertNull(throttledState.loginLockedUntil());

    loginExpectTooManyRequests("alice", "password123!", "login-ip-throttle-003", sharedIp);
    assertTrue(output.getOut().contains("auth login throttled"));
    assertTrue(output.getOut().contains("scope=IP"));

    String token = login("alice", "password123!", "login-ip-throttle-004", otherIp);
    assertNotNull(token);
  }

  @Test
  void throttlesGlobalLoginBurstAcrossDifferentIpsAndRecoversAfterWindow() throws Exception {
    loginExpectUnauthorized(
        "missing-user-1",
        "wrong-password",
        "login-global-throttle-001",
        clientMetadata("198.51.100.10"));
    loginExpectUnauthorized(
        "missing-user-2",
        "wrong-password",
        "login-global-throttle-002",
        clientMetadata("198.51.100.11"));
    loginExpectUnauthorized(
        "missing-user-3",
        "wrong-password",
        "login-global-throttle-003",
        clientMetadata("198.51.100.12"));
    loginExpectUnauthorized(
        "missing-user-4",
        "wrong-password",
        "login-global-throttle-004",
        clientMetadata("198.51.100.13"));

    loginExpectTooManyRequests(
        "alice", "password123!", "login-global-throttle-005", clientMetadata("198.51.100.14"));

    Thread.sleep(1200L);

    String token =
        login(
            "alice", "password123!", "login-global-throttle-006", clientMetadata("198.51.100.14"));
    assertNotNull(token);
  }

  private String login(
      String loginId, String password, String requestId, RequestClientMetadata clientMetadata)
      throws Exception {
    MvcResult result =
        performLogin(loginId, password, requestId, clientMetadata)
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(result.getResponse().getContentAsByteArray())
        .get("accessToken")
        .asText();
  }

  private void loginExpectUnauthorized(
      String loginId, String password, String requestId, RequestClientMetadata clientMetadata)
      throws Exception {
    performLogin(loginId, password, requestId, clientMetadata)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("login failed"));
  }

  private void loginExpectTooManyRequests(
      String loginId, String password, String requestId, RequestClientMetadata clientMetadata)
      throws Exception {
    performLogin(loginId, password, requestId, clientMetadata)
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.message").value("too many login attempts"))
        .andExpect(header().string("Retry-After", "1"));
  }

  private org.springframework.test.web.servlet.ResultActions performLogin(
      String loginId, String password, String requestId, RequestClientMetadata clientMetadata)
      throws Exception {
    MockHttpServletRequestBuilder requestBuilder =
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "loginId": "%s",
                  "password": "%s"
                }
                """
                    .formatted(loginId, password));
    if (requestId != null && !requestId.isBlank()) {
      requestBuilder.header("X-Request-Id", requestId);
    }
    requestBuilder.header("User-Agent", clientMetadata.userAgent());
    requestBuilder.header("X-Forwarded-For", clientMetadata.forwardedFor());
    return mockMvc.perform(requestBuilder);
  }

  private long bootstrapUser(String loginId, String displayName, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/internal/api/v1/auth/users/bootstrap")
                    .header(
                        "Authorization",
                        internalServiceAuthorization(
                            "auth-bootstrap", InternalServiceScope.AUTH_BOOTSTRAP))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "loginId": "%s",
                          "password": "%s",
                          "displayName": "%s"
                        }
                        """
                            .formatted(loginId, password, displayName)))
            .andExpect(status().isOk())
            .andReturn();

    return objectMapper
        .readTree(result.getResponse().getContentAsByteArray())
        .get("userId")
        .asLong();
  }

  private String internalServiceAuthorization(String subject, InternalServiceScope scope) {
    return "Bearer " + internalServiceTokenIssuer.issue(subject, java.util.Set.of(scope));
  }

  private LoginProtectionState loadLoginProtectionState(long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT failed_login_count,
                   last_login_failed_at,
                   login_locked_until,
                   last_login_succeeded_at
            FROM bank_user
            WHERE id = :userId
            """,
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("userId", userId),
            (rs, rowNum) ->
                new LoginProtectionState(
                    rs.getInt("failed_login_count"),
                    toInstant(rs.getTimestamp("last_login_failed_at")),
                    toInstant(rs.getTimestamp("login_locked_until")),
                    toInstant(rs.getTimestamp("last_login_succeeded_at"))))
        .stream()
        .findFirst()
        .orElseThrow(() -> new AssertionError("login protection state is not found"));
  }

  private Instant toInstant(java.sql.Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }

  private RequestClientMetadata clientMetadata(String ipAddress) {
    return new RequestClientMetadata(WINDOWS_CHROME.userAgent(), ipAddress + ", 10.0.0.1");
  }

  private record LoginProtectionState(
      int failedLoginCount,
      Instant lastLoginFailedAt,
      Instant loginLockedUntil,
      Instant lastLoginSucceededAt) {}

  private record RequestClientMetadata(String userAgent, String forwardedFor) {}
}
