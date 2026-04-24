package com.aquilabank.global.web.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.auth.port.RefreshTokenSecretPort;
import com.aquilabank.domain.auth.port.RememberDeviceSecretPort;
import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenIssuer;
import com.aquilabank.global.security.LoginThrottleGuard;
import com.aquilabank.standard.util.Base32Codec;
import com.aquilabank.support.PostgresContainerTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import jakarta.servlet.http.Cookie;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
import org.springframework.transaction.PlatformTransactionManager;
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
      "security.jwt.refresh-token-ttl-seconds=2",
      "security.totp.secret-encryption-key=test-local-totp-secret-encryption-key"
    })
class LoginAndAccountAccessApiIntegrationTest extends PostgresContainerTestSupport {

  private static final String AUTH_ADMIN_SUBJECT = "ops-admin";
  private static final String TEST_SECRET = "test-local-jwt-secret-test-local-jwt-secret";
  private static final String REFRESH_DEVICE_COOKIE_NAME = "ab_refresh_device";
  private static final String REMEMBER_DEVICE_COOKIE_NAME = "ab_mfa_remember_device";
  private static final RequestClientMetadata WINDOWS_CHROME =
      new RequestClientMetadata(
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
              + "(KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36",
          "203.0.113.10, 10.0.0.1",
          "Windows / Chrome",
          "203.0.113.10");
  private static final RequestClientMetadata IPHONE_SAFARI =
      new RequestClientMetadata(
          "Mozilla/5.0 (iPhone; CPU iPhone OS 18_3 like Mac OS X) AppleWebKit/605.1.15 "
              + "(KHTML, like Gecko) Version/18.3 Mobile/15E148 Safari/604.1",
          "198.51.100.24, 10.0.0.2",
          "iPhone / Safari",
          "198.51.100.24");
  private static final RequestClientMetadata WINDOWS_EDGE =
      new RequestClientMetadata(
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
              + "(KHTML, like Gecko) Chrome/135.0.0.0 Safari/537.36 Edg/135.0.0.0",
          "192.0.2.77, 10.0.0.3",
          "Windows / Edge",
          "192.0.2.77");

  @Autowired private WebApplicationContext context;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private RefreshTokenSecretPort refreshTokenSecretPort;

  @Autowired private RememberDeviceSecretPort rememberDeviceSecretPort;

  @Autowired private LoginThrottleGuard loginThrottleGuard;

  @Autowired private InternalServiceTokenIssuer internalServiceTokenIssuer;

  @Autowired private PlatformTransactionManager transactionManager;

  private MockMvc mockMvc;
  private long userId;
  private long allowedSourceAccountId;
  private long targetAccountId;
  private long deniedAccountId;
  private final Map<String, String> refreshDeviceBindingTokenByRefreshToken = new HashMap<>();

  @BeforeEach
  void setUpDatabase() throws Exception {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    resetBankingTables(jdbcTemplate);
    loginThrottleGuard.clear();
    refreshDeviceBindingTokenByRefreshToken.clear();

    allowedSourceAccountId = bootstrapAccount("allowed source", 10_000L);
    targetAccountId = bootstrapAccount("allowed target", 0L);
    deniedAccountId = bootstrapAccount("denied source", 5_000L);

    userId = bootstrapUser("alice", "Alice", "password123!");
    upsertMembership(userId, allowedSourceAccountId, "OWNER", "ACTIVE");
    upsertVerifiedContact(userId, "EMAIL", "alice.recovery@example.com");
  }

  @Test
  void loginIssuesJwtAndAllowsMappedAccountFlow() throws Exception {
    String token = login("alice", "password123!");

    MvcResult transferResult =
        mockMvc
            .perform(
                post("/api/v1/transfers")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Request-Id", "jwt-transfer-request")
                    .header("Idempotency-Key", "jwt-transfer-001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "sourceAccountId": %d,
                          "targetAccountId": %d,
                          "amountMinor": 1500,
                          "currencyCode": "KRW",
                          "summary": "rent"
                        }
                        """
                            .formatted(allowedSourceAccountId, targetAccountId)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sourceAccountId").value(allowedSourceAccountId))
            .andReturn();

    assertEquals("jwt-transfer-request", transferResult.getResponse().getHeader("X-Request-Id"));

    mockMvc
        .perform(
            get("/api/v1/transactions")
                .header("Authorization", "Bearer " + token)
                .param("accountId", String.valueOf(allowedSourceAccountId))
                .param("from", transactionQueryFrom())
                .param("to", transactionQueryTo()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].accountId").value(allowedSourceAccountId))
        .andExpect(jsonPath("$.items[0].transactionReference").isString());

    mockMvc
        .perform(
            get("/api/v1/accounts/%d".formatted(allowedSourceAccountId))
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(allowedSourceAccountId))
        .andExpect(jsonPath("$.currencyCode").value("KRW"))
        .andExpect(jsonPath("$.availableBalanceMinor").value(8500L))
        .andExpect(jsonPath("$.pendingBalanceMinor").value(0));

    mockMvc
        .perform(get("/api/v1/accounts").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].accountId").value(allowedSourceAccountId))
        .andExpect(jsonPath("$.items[0].availableBalanceMinor").value(8500L));

    String transactionReference = transactionReference(transferResult);

    mockMvc
        .perform(
            get("/api/v1/transactions")
                .header("Authorization", "Bearer " + token)
                .param("accountId", String.valueOf(allowedSourceAccountId))
                .param("from", transactionQueryFrom())
                .param("to", transactionQueryTo())
                .param("status", "BOOKED")
                .param("direction", "DEBIT")
                .param("minAmountMinor", "1500")
                .param("maxAmountMinor", "1500")
                .param("transactionReference", transactionReference))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].transactionReference").value(transactionReference))
        .andExpect(jsonPath("$.items[0].direction").value("DEBIT"))
        .andExpect(jsonPath("$.items[0].amountMinor").value(1500L));
  }

  @Test
  void accountListReturnsOnlyActiveAccessibleAccountsAndBootstrapAccount() throws Exception {
    upsertMembership(userId, targetAccountId, "VIEWER", "ACTIVE");
    upsertMembership(userId, deniedAccountId, "OWNER", "REVOKED");
    String token = login("alice", "password123!");

    mockMvc
        .perform(get("/api/v1/accounts").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].accountId").value(allowedSourceAccountId))
        .andExpect(jsonPath("$.items[1].accountId").value(targetAccountId));

    mockMvc
        .perform(
            get("/api/v1/accounts")
                .header("X-Account-Id", String.valueOf(targetAccountId))
                .header("X-Subject", "bootstrap-account"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].accountId").value(targetAccountId))
        .andExpect(jsonPath("$.items[0].availableBalanceMinor").value(0L));
  }

  @Test
  void lockedAccountAllowsReadButBlocksTransferAndReversal() throws Exception {
    String token = login("alice", "password123!");

    MvcResult bookedTransfer =
        mockMvc
            .perform(
                post("/api/v1/transfers")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Request-Id", "jwt-locked-booked-001")
                    .header("Idempotency-Key", "jwt-locked-booked-001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "sourceAccountId": %d,
                          "targetAccountId": %d,
                          "amountMinor": 1500,
                          "currencyCode": "KRW",
                          "summary": "rent"
                        }
                        """
                            .formatted(allowedSourceAccountId, targetAccountId)))
            .andExpect(status().isOk())
            .andReturn();

    String transactionReference = transactionReference(bookedTransfer);
    updateAccountStatus(allowedSourceAccountId, "LOCKED", "account-locked-request");

    mockMvc
        .perform(
            get("/api/v1/accounts/%d".formatted(allowedSourceAccountId))
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountStatus").value("LOCKED"))
        .andExpect(jsonPath("$.availableBalanceMinor").value(8500L));

    mockMvc
        .perform(
            get("/api/v1/transactions")
                .header("Authorization", "Bearer " + token)
                .param("accountId", String.valueOf(allowedSourceAccountId))
                .param("from", transactionQueryFrom())
                .param("to", transactionQueryTo()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].accountId").value(allowedSourceAccountId));

    mockMvc
        .perform(
            get("/api/v1/transactions/%s".formatted(transactionReference))
                .header("Authorization", "Bearer " + token)
                .param("accountId", String.valueOf(allowedSourceAccountId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionReference").value(transactionReference));

    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("Authorization", "Bearer " + token)
                .header("X-Request-Id", "jwt-locked-transfer-403")
                .header("Idempotency-Key", "jwt-locked-transfer-403")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": %d,
                      "targetAccountId": %d,
                      "amountMinor": 500,
                      "currencyCode": "KRW",
                      "summary": "locked-blocked"
                    }
                    """
                        .formatted(allowedSourceAccountId, targetAccountId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));

    mockMvc
        .perform(
            post("/api/v1/transfers/%s/reversal".formatted(transactionReference))
                .header("Authorization", "Bearer " + token)
                .header("X-Request-Id", "jwt-locked-reversal-403")
                .header("Idempotency-Key", "jwt-locked-reversal-403")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": %d,
                      "reversalReason": "CANCEL",
                      "summary": "locked-reversal-blocked"
                    }
                    """
                        .formatted(allowedSourceAccountId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));
  }

  @Test
  void closedAccountBlocksReadTransferAndReversal() throws Exception {
    String token = login("alice", "password123!");

    MvcResult bookedTransfer =
        mockMvc
            .perform(
                post("/api/v1/transfers")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Request-Id", "jwt-closed-booked-001")
                    .header("Idempotency-Key", "jwt-closed-booked-001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "sourceAccountId": %d,
                          "targetAccountId": %d,
                          "amountMinor": 1500,
                          "currencyCode": "KRW",
                          "summary": "rent"
                        }
                        """
                            .formatted(allowedSourceAccountId, targetAccountId)))
            .andExpect(status().isOk())
            .andReturn();

    String transactionReference = transactionReference(bookedTransfer);
    updateAccountStatus(allowedSourceAccountId, "CLOSED", "account-closed-request");

    mockMvc
        .perform(
            get("/api/v1/accounts/%d".formatted(allowedSourceAccountId))
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));

    mockMvc
        .perform(
            get("/api/v1/transactions")
                .header("Authorization", "Bearer " + token)
                .param("accountId", String.valueOf(allowedSourceAccountId))
                .param("from", transactionQueryFrom())
                .param("to", transactionQueryTo()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));

    mockMvc
        .perform(
            get("/api/v1/transactions/%s".formatted(transactionReference))
                .header("Authorization", "Bearer " + token)
                .param("accountId", String.valueOf(allowedSourceAccountId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));

    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("Authorization", "Bearer " + token)
                .header("X-Request-Id", "jwt-closed-transfer-403")
                .header("Idempotency-Key", "jwt-closed-transfer-403")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": %d,
                      "targetAccountId": %d,
                      "amountMinor": 500,
                      "currencyCode": "KRW",
                      "summary": "closed-blocked"
                    }
                    """
                        .formatted(allowedSourceAccountId, targetAccountId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));

    mockMvc
        .perform(
            post("/api/v1/transfers/%s/reversal".formatted(transactionReference))
                .header("Authorization", "Bearer " + token)
                .header("X-Request-Id", "jwt-closed-reversal-403")
                .header("Idempotency-Key", "jwt-closed-reversal-403")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": %d,
                      "reversalReason": "CANCEL",
                      "summary": "closed-reversal-blocked"
                    }
                    """
                        .formatted(allowedSourceAccountId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));
  }

  @Test
  void bootstrapAccountPrincipalAllowsLockedReadButRejectsClosedRead() throws Exception {
    updateAccountStatus(allowedSourceAccountId, "LOCKED", "bootstrap-account-locked-request");

    mockMvc
        .perform(
            get("/api/v1/accounts/%d".formatted(allowedSourceAccountId))
                .header("X-Account-Id", String.valueOf(allowedSourceAccountId))
                .header("X-Subject", "bootstrap-account"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountStatus").value("LOCKED"));

    updateAccountStatus(allowedSourceAccountId, "CLOSED", "bootstrap-account-closed-request");

    mockMvc
        .perform(
            get("/api/v1/accounts/%d".formatted(allowedSourceAccountId))
                .header("X-Account-Id", String.valueOf(allowedSourceAccountId))
                .header("X-Subject", "bootstrap-account"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));
  }

  @Test
  void accountListKeepsLockedAccountsButHidesClosedAccounts() throws Exception {
    upsertMembership(userId, targetAccountId, "VIEWER", "ACTIVE");
    String token = login("alice", "password123!");

    updateAccountStatus(allowedSourceAccountId, "LOCKED", "account-list-locked-request");
    updateAccountStatus(targetAccountId, "CLOSED", "account-list-closed-request");

    mockMvc
        .perform(get("/api/v1/accounts").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].accountId").value(allowedSourceAccountId))
        .andExpect(jsonPath("$.items[0].accountStatus").value("LOCKED"));
  }

  @Test
  void bootstrapAccountListAllowsLockedAccountButRejectsClosedAccount() throws Exception {
    updateAccountStatus(allowedSourceAccountId, "LOCKED", "bootstrap-account-list-locked-request");

    mockMvc
        .perform(
            get("/api/v1/accounts")
                .header("X-Account-Id", String.valueOf(allowedSourceAccountId))
                .header("X-Subject", "bootstrap-account"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].accountId").value(allowedSourceAccountId))
        .andExpect(jsonPath("$.items[0].accountStatus").value("LOCKED"));

    updateAccountStatus(allowedSourceAccountId, "CLOSED", "bootstrap-account-list-closed-request");

    mockMvc
        .perform(
            get("/api/v1/accounts")
                .header("X-Account-Id", String.valueOf(allowedSourceAccountId))
                .header("X-Subject", "bootstrap-account"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));
  }

  @Test
  void transactionDetailReturnsAllowedAccountDataAnd404ForMissingReference() throws Exception {
    String token = login("alice", "password123!");

    MvcResult transferResult =
        mockMvc
            .perform(
                post("/api/v1/transfers")
                    .header("Authorization", "Bearer " + token)
                    .header("X-Request-Id", "jwt-detail-request")
                    .header("Idempotency-Key", "jwt-detail-001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "sourceAccountId": %d,
                          "targetAccountId": %d,
                          "amountMinor": 1500,
                          "currencyCode": "KRW",
                          "summary": "rent"
                        }
                        """
                            .formatted(allowedSourceAccountId, targetAccountId)))
            .andExpect(status().isOk())
            .andReturn();

    String transactionReference = transactionReference(transferResult);

    mockMvc
        .perform(
            get("/api/v1/transactions/%s".formatted(transactionReference))
                .header("Authorization", "Bearer " + token)
                .param("accountId", String.valueOf(allowedSourceAccountId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(allowedSourceAccountId))
        .andExpect(jsonPath("$.transactionReference").value(transactionReference))
        .andExpect(jsonPath("$.direction").value("DEBIT"))
        .andExpect(jsonPath("$.transactionStatus").value("BOOKED"))
        .andExpect(jsonPath("$.entryStatus").value("BOOKED"))
        .andExpect(jsonPath("$.entryReference").isString())
        .andExpect(jsonPath("$.summary").value("rent"));

    mockMvc
        .perform(
            get("/api/v1/transactions/TRX-MISSING-DETAIL")
                .header("Authorization", "Bearer " + token)
                .param("accountId", String.valueOf(allowedSourceAccountId)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("transaction detail is not found"));
  }

  @Test
  void rejectsUnmappedAccountAccess() throws Exception {
    String token = login("alice", "password123!");

    mockMvc
        .perform(
            get("/api/v1/accounts/%d".formatted(deniedAccountId))
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));

    mockMvc
        .perform(
            get("/api/v1/transactions")
                .header("Authorization", "Bearer " + token)
                .param("accountId", String.valueOf(deniedAccountId))
                .param("from", transactionQueryFrom())
                .param("to", transactionQueryTo()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));

    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "jwt-transfer-403")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": %d,
                      "targetAccountId": %d,
                      "amountMinor": 500,
                      "currencyCode": "KRW",
                      "summary": "blocked"
                    }
                    """
                        .formatted(deniedAccountId, targetAccountId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));
  }

  @Test
  void transactionDetailRejectsUnmappedAccountAndBootstrapMismatch() throws Exception {
    String token = login("alice", "password123!");

    mockMvc
        .perform(
            get("/api/v1/transactions/TRX-DENIED")
                .header("Authorization", "Bearer " + token)
                .param("accountId", String.valueOf(deniedAccountId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));

    mockMvc
        .perform(
            get("/api/v1/transactions/TRX-BOOTSTRAP-MISMATCH")
                .header("X-Account-Id", String.valueOf(allowedSourceAccountId))
                .header("X-Subject", "bootstrap-account")
                .param("accountId", String.valueOf(targetAccountId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));
  }

  @Test
  void membershipUpsertUpdatesTransferPermission() throws Exception {
    upsertMembership(userId, allowedSourceAccountId, "VIEWER", "ACTIVE");
    String token = login("alice", "password123!");

    mockMvc
        .perform(
            get("/api/v1/transactions")
                .header("Authorization", "Bearer " + token)
                .param("accountId", String.valueOf(allowedSourceAccountId))
                .param("from", transactionQueryFrom())
                .param("to", transactionQueryTo()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].accountId").value(allowedSourceAccountId));

    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "jwt-transfer-viewer-403")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": %d,
                      "targetAccountId": %d,
                      "amountMinor": 500,
                      "currencyCode": "KRW",
                      "summary": "viewer-blocked"
                    }
                    """
                        .formatted(allowedSourceAccountId, targetAccountId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));
  }

  @Test
  void rejectsInvalidLoginCredentials() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "loginId": "alice",
                      "password": "wrong-password"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("login failed"));
  }

  @Test
  void locksUserAfterThresholdAndResetsStateAfterSuccessfulLogin() throws Exception {
    loginExpectUnauthorized("alice", "wrong-password", "login-failure-001");
    loginExpectUnauthorized("alice", "wrong-password", "login-failure-002");
    loginExpectUnauthorized("alice", "wrong-password", "login-failure-003");

    LoginProtectionState lockedState = loadLoginProtectionState(userId);
    assertEquals(3, lockedState.failedLoginCount());
    assertNotNull(lockedState.lastLoginFailedAt());
    assertNotNull(lockedState.loginLockedUntil());
    assertNull(lockedState.lastLoginSucceededAt());

    loginExpectUnauthorized("alice", "password123!", "login-locked-001");
    Thread.sleep(1200L);

    String token = login("alice", "password123!", "login-reset-001");
    assertNotNull(token);

    LoginProtectionState resetState = loadLoginProtectionState(userId);
    assertEquals(0, resetState.failedLoginCount());
    assertNull(resetState.lastLoginFailedAt());
    assertNull(resetState.loginLockedUntil());
    assertNotNull(resetState.lastLoginSucceededAt());
  }

  @Test
  void lockedUserStatusBlocksLogin() throws Exception {
    updateLegacyUserStatus(userId, "LOCKED", "manual-lock", "user-locked-request");

    loginExpectUnauthorized("alice", "password123!", "login-user-locked-001");
  }

  @Test
  void refreshRotatesTokenAndRejectsReusedToken() throws Exception {
    TokenPairResponseView loginResult =
        loginResult("alice", "password123!", "refresh-login-001", WINDOWS_CHROME);
    assertNotNull(loginResult.refreshToken());
    assertNotNull(loginResult.refreshExpiresAt());

    RefreshTokenSessionView initialSession = loadRefreshTokenSession(loginResult.refreshToken());
    assertEquals("ACTIVE", initialSession.sessionStatus());
    assertEquals(WINDOWS_CHROME.expectedDeviceName(), initialSession.deviceName());
    assertEquals(WINDOWS_CHROME.expectedIpAddress(), initialSession.ipAddress());

    TokenPairResponseView refreshed =
        refresh(loginResult.refreshToken(), "refresh-rotate-001", WINDOWS_EDGE);
    assertNotNull(refreshed.accessToken());
    assertNotNull(refreshed.refreshToken());
    assertNotEquals(loginResult.refreshToken(), refreshed.refreshToken());

    RefreshTokenSessionView rotatedSession = loadRefreshTokenSession(loginResult.refreshToken());
    assertEquals("ROTATED", rotatedSession.sessionStatus());
    assertNotNull(rotatedSession.lastUsedAt());
    assertNotNull(rotatedSession.rotatedAt());
    assertNotNull(rotatedSession.replacedBySessionId());

    RefreshTokenSessionView newSession = loadRefreshTokenSession(refreshed.refreshToken());
    assertEquals("ACTIVE", newSession.sessionStatus());
    assertNull(newSession.lastUsedAt());
    assertNull(newSession.rotatedAt());
    assertEquals(WINDOWS_EDGE.expectedDeviceName(), newSession.deviceName());
    assertEquals(WINDOWS_EDGE.expectedIpAddress(), newSession.ipAddress());

    refreshExpectUnauthorized(loginResult.refreshToken(), "refresh-reuse-001");

    RefreshTokenSessionView revokedDescendant = loadRefreshTokenSession(refreshed.refreshToken());
    assertEquals("REVOKED", revokedDescendant.sessionStatus());
    assertNotNull(revokedDescendant.lastUsedAt());
    refreshExpectUnauthorized(refreshed.refreshToken(), "refresh-descendant-after-reuse-001");
  }

  @Test
  void refreshRejectsMissingDeviceBindingCookie() throws Exception {
    TokenPairResponseView loginResult =
        loginResult("alice", "password123!", "refresh-missing-cookie-login-001", WINDOWS_CHROME);

    refreshDeviceBindingTokenByRefreshToken.remove(loginResult.refreshToken());

    refreshExpectUnauthorized(loginResult.refreshToken(), "refresh-missing-cookie-001");
  }

  @Test
  void passwordResetChangesPasswordAndRevokesActiveRefreshSessions() throws Exception {
    TokenPairResponseView currentSession =
        loginResult("alice", "password123!", "password-reset-login-001");
    TokenPairResponseView parallelSession =
        loginResult("alice", "password123!", "password-reset-login-002");

    passwordReset(
            currentSession.accessToken(),
            "password123!",
            "newPassword456!",
            "password-reset-request-001")
        .andExpect(status().isNoContent());

    RefreshTokenSessionView currentRefresh = loadRefreshTokenSession(currentSession.refreshToken());
    RefreshTokenSessionView parallelRefresh =
        loadRefreshTokenSession(parallelSession.refreshToken());
    assertEquals("REVOKED", currentRefresh.sessionStatus());
    assertEquals("REVOKED", parallelRefresh.sessionStatus());
    assertNotNull(currentRefresh.lastUsedAt());
    assertNotNull(parallelRefresh.lastUsedAt());

    refreshExpectUnauthorized(currentSession.refreshToken(), "password-reset-refresh-001");
    refreshExpectUnauthorized(parallelSession.refreshToken(), "password-reset-refresh-002");
    loginExpectUnauthorized("alice", "password123!", "password-reset-old-login-001");
    assertNotNull(login("alice", "newPassword456!", "password-reset-new-login-001"));
  }

  @Test
  void passwordResetRejectsWrongCurrentPassword() throws Exception {
    TokenPairResponseView currentSession =
        loginResult("alice", "password123!", "password-reset-wrong-login-001");

    passwordResetExpectUnauthorized(
        currentSession.accessToken(),
        "wrong-password",
        "newPassword456!",
        "password-reset-wrong-request-001");

    RefreshTokenSessionView currentRefresh = loadRefreshTokenSession(currentSession.refreshToken());
    assertEquals("ACTIVE", currentRefresh.sessionStatus());
    assertNotNull(login("alice", "password123!", "password-reset-wrong-old-login-001"));
  }

  @Test
  void disabledUserCannotResetPassword() throws Exception {
    TokenPairResponseView currentSession =
        loginResult("alice", "password123!", "password-reset-disabled-login-001");
    updateLegacyUserStatus(userId, "DISABLED", "fraud-review", "password-reset-disabled-ops-001");

    passwordReset(
            currentSession.accessToken(),
            "password123!",
            "newPassword456!",
            "password-reset-disabled-request-001")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("current session is not active"));
  }

  @Test
  void authSessionListReturnsOnlyCurrentUsersUnexpiredActiveSessions() throws Exception {
    TokenPairResponseView expired =
        loginResult("alice", "password123!", "session-list-login-001", WINDOWS_CHROME);
    Thread.sleep(2200L);
    TokenPairResponseView oldest =
        loginResult("alice", "password123!", "session-list-login-002", WINDOWS_CHROME);
    TokenPairResponseView middle =
        loginResult("alice", "password123!", "session-list-login-003", IPHONE_SAFARI);
    TokenPairResponseView newest =
        loginResult("alice", "password123!", "session-list-login-004", WINDOWS_EDGE);
    TokenPairResponseView revoked =
        loginResult("alice", "password123!", "session-list-login-005", WINDOWS_CHROME);
    long otherUserId = bootstrapUser("bob", "Bob", "password123!");
    upsertMembership(otherUserId, targetAccountId, "VIEWER", "ACTIVE");
    TokenPairResponseView otherUser =
        loginResult("bob", "password123!", "session-list-bob-login-001");
    logout(newest.accessToken(), revoked.refreshToken(), "session-list-logout-001")
        .andExpect(status().isNoContent());

    refreshExpectUnauthorized(expired.refreshToken(), "session-list-expired-refresh-001");
    refresh(otherUser.refreshToken(), "session-list-bob-refresh-001");

    long newestSessionId = loadRefreshTokenSessionId(newest.refreshToken());
    long middleSessionId = loadRefreshTokenSessionId(middle.refreshToken());
    long oldestSessionId = loadRefreshTokenSessionId(oldest.refreshToken());

    mockMvc
        .perform(
            get("/api/v1/auth/sessions")
                .header("Authorization", "Bearer " + newest.accessToken())
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(3))
        .andExpect(jsonPath("$.items[0].sessionId").value(newestSessionId))
        .andExpect(jsonPath("$.items[0].currentSession").value(true))
        .andExpect(jsonPath("$.items[0].sessionStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.items[0].deviceName").value(WINDOWS_EDGE.expectedDeviceName()))
        .andExpect(jsonPath("$.items[0].ipAddress").value(WINDOWS_EDGE.expectedIpAddress()))
        .andExpect(jsonPath("$.items[1].sessionId").value(middleSessionId))
        .andExpect(jsonPath("$.items[1].currentSession").value(false))
        .andExpect(jsonPath("$.items[1].deviceName").value(IPHONE_SAFARI.expectedDeviceName()))
        .andExpect(jsonPath("$.items[1].ipAddress").value(IPHONE_SAFARI.expectedIpAddress()))
        .andExpect(jsonPath("$.items[2].sessionId").value(oldestSessionId))
        .andExpect(jsonPath("$.items[2].currentSession").value(false))
        .andExpect(jsonPath("$.items[2].deviceName").value(WINDOWS_CHROME.expectedDeviceName()))
        .andExpect(jsonPath("$.items[2].ipAddress").value(WINDOWS_CHROME.expectedIpAddress()))
        .andExpect(jsonPath("$.items[0].createdAt").isString());
  }

  @Test
  void authSessionListMarksRefreshedSessionAsCurrent() throws Exception {
    TokenPairResponseView oldest =
        loginResult("alice", "password123!", "session-current-refresh-login-001", WINDOWS_CHROME);
    TokenPairResponseView current =
        loginResult("alice", "password123!", "session-current-refresh-login-002", WINDOWS_EDGE);
    TokenPairResponseView refreshed =
        refresh(current.refreshToken(), "session-current-refresh-refresh-001", IPHONE_SAFARI);

    long refreshedSessionId = loadRefreshTokenSessionId(refreshed.refreshToken());
    long oldestSessionId = loadRefreshTokenSessionId(oldest.refreshToken());

    mockMvc
        .perform(
            get("/api/v1/auth/sessions")
                .header("Authorization", "Bearer " + refreshed.accessToken())
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].sessionId").value(refreshedSessionId))
        .andExpect(jsonPath("$.items[0].currentSession").value(true))
        .andExpect(jsonPath("$.items[1].sessionId").value(oldestSessionId))
        .andExpect(jsonPath("$.items[1].currentSession").value(false));
  }

  @Test
  void authSessionListAllowsLegacyJwtWithoutSessionIdAndMarksNoCurrentSession() throws Exception {
    TokenPairResponseView oldest =
        loginResult("alice", "password123!", "session-legacy-login-001", WINDOWS_CHROME);
    TokenPairResponseView newest =
        loginResult("alice", "password123!", "session-legacy-login-002", WINDOWS_EDGE);
    long newestSessionId = loadRefreshTokenSessionId(newest.refreshToken());
    long oldestSessionId = loadRefreshTokenSessionId(oldest.refreshToken());
    String legacyAccessToken = issueLegacyAccessToken("alice", userId);

    mockMvc
        .perform(
            get("/api/v1/auth/sessions")
                .header("Authorization", "Bearer " + legacyAccessToken)
                .param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].sessionId").value(newestSessionId))
        .andExpect(jsonPath("$.items[0].currentSession").value(false))
        .andExpect(jsonPath("$.items[1].sessionId").value(oldestSessionId))
        .andExpect(jsonPath("$.items[1].currentSession").value(false));
  }

  @Test
  void authSessionListHonorsSizeLimit() throws Exception {
    TokenPairResponseView oldest =
        loginResult("alice", "password123!", "session-list-size-login-001");
    TokenPairResponseView middle =
        loginResult("alice", "password123!", "session-list-size-login-002");
    TokenPairResponseView newest =
        loginResult("alice", "password123!", "session-list-size-login-003");
    long newestSessionId = loadRefreshTokenSessionId(newest.refreshToken());
    long middleSessionId = loadRefreshTokenSessionId(middle.refreshToken());
    assertNotNull(loadRefreshTokenSession(oldest.refreshToken()));

    mockMvc
        .perform(
            get("/api/v1/auth/sessions")
                .header("Authorization", "Bearer " + newest.accessToken())
                .param("size", "2"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].sessionId").value(newestSessionId))
        .andExpect(jsonPath("$.items[1].sessionId").value(middleSessionId));
  }

  @Test
  void expiredRefreshTokenIsRejected() throws Exception {
    TokenPairResponseView loginResult = loginResult("alice", "password123!", "refresh-expire-001");
    Thread.sleep(2200L);
    refreshExpectUnauthorized(loginResult.refreshToken(), "refresh-expire-002");
  }

  @Test
  void logoutRevokesRefreshTokenSessionAndBlocksRefresh() throws Exception {
    TokenPairResponseView loginResult = loginResult("alice", "password123!", "logout-login-001");

    logout(loginResult.accessToken(), loginResult.refreshToken(), "logout-request-001")
        .andExpect(status().isNoContent());

    RefreshTokenSessionView revokedSession = loadRefreshTokenSession(loginResult.refreshToken());
    assertEquals("REVOKED", revokedSession.sessionStatus());
    assertNotNull(revokedSession.lastUsedAt());
    assertNull(revokedSession.rotatedAt());
    assertNull(revokedSession.replacedBySessionId());

    refreshExpectUnauthorized(loginResult.refreshToken(), "logout-refresh-001");
  }

  @Test
  void logoutKeepsOtherUsersRefreshTokenSessionUntouched() throws Exception {
    TokenPairResponseView alice = loginResult("alice", "password123!", "logout-alice-001");
    long otherUserId = bootstrapUser("bob", "Bob", "password123!");
    upsertMembership(otherUserId, targetAccountId, "VIEWER", "ACTIVE");
    TokenPairResponseView bob = loginResult("bob", "password123!", "logout-bob-001");

    logout(alice.accessToken(), bob.refreshToken(), "logout-other-user-001")
        .andExpect(status().isNoContent());

    RefreshTokenSessionView bobSession = loadRefreshTokenSession(bob.refreshToken());
    assertEquals("ACTIVE", bobSession.sessionStatus());
    assertNull(bobSession.lastUsedAt());

    refresh(bob.refreshToken(), "logout-bob-refresh-001");
  }

  @Test
  void revokeSelectedSessionBySessionIdRevokesOnlyThatSession() throws Exception {
    TokenPairResponseView oldest =
        loginResult("alice", "password123!", "session-revoke-select-login-001");
    TokenPairResponseView target =
        loginResult("alice", "password123!", "session-revoke-select-login-002");
    TokenPairResponseView newest =
        loginResult("alice", "password123!", "session-revoke-select-login-003");
    long targetSessionId = loadRefreshTokenSessionId(target.refreshToken());

    revokeSession(newest.accessToken(), targetSessionId, "session-revoke-select-001")
        .andExpect(status().isNoContent());

    RefreshTokenSessionView oldestSession = loadRefreshTokenSession(oldest.refreshToken());
    RefreshTokenSessionView targetSession = loadRefreshTokenSession(target.refreshToken());
    RefreshTokenSessionView newestSession = loadRefreshTokenSession(newest.refreshToken());
    assertEquals("ACTIVE", oldestSession.sessionStatus());
    assertEquals("REVOKED", targetSession.sessionStatus());
    assertNotNull(targetSession.lastUsedAt());
    assertNull(targetSession.rotatedAt());
    assertNull(targetSession.replacedBySessionId());
    assertEquals("ACTIVE", newestSession.sessionStatus());

    refreshExpectUnauthorized(target.refreshToken(), "session-revoke-select-refresh-001");
  }

  @Test
  void revokeSelectedSessionKeepsOtherUsersSessionUntouched() throws Exception {
    TokenPairResponseView alice =
        loginResult("alice", "password123!", "session-revoke-other-login-001");
    long otherUserId = bootstrapUser("bob", "Bob", "password123!");
    upsertMembership(otherUserId, targetAccountId, "VIEWER", "ACTIVE");
    TokenPairResponseView bob =
        loginResult("bob", "password123!", "session-revoke-other-login-002");
    long bobSessionId = loadRefreshTokenSessionId(bob.refreshToken());

    revokeSession(alice.accessToken(), bobSessionId, "session-revoke-other-001")
        .andExpect(status().isNoContent());

    RefreshTokenSessionView bobSession = loadRefreshTokenSession(bob.refreshToken());
    assertEquals("ACTIVE", bobSession.sessionStatus());
    assertNull(bobSession.lastUsedAt());
    refresh(bob.refreshToken(), "session-revoke-other-refresh-001");
  }

  @Test
  void revokeAllSessionsRevokesOnlyCurrentUsersActiveSessions() throws Exception {
    TokenPairResponseView oldest =
        loginResult("alice", "password123!", "session-revoke-all-login-001");
    TokenPairResponseView middle =
        loginResult("alice", "password123!", "session-revoke-all-login-002");
    TokenPairResponseView newest =
        loginResult("alice", "password123!", "session-revoke-all-login-003");
    long otherUserId = bootstrapUser("bob", "Bob", "password123!");
    upsertMembership(otherUserId, targetAccountId, "VIEWER", "ACTIVE");
    TokenPairResponseView bob = loginResult("bob", "password123!", "session-revoke-all-login-004");

    revokeAllSessions(newest.accessToken(), "session-revoke-all-001")
        .andExpect(status().isNoContent());

    RefreshTokenSessionView oldestSession = loadRefreshTokenSession(oldest.refreshToken());
    RefreshTokenSessionView middleSession = loadRefreshTokenSession(middle.refreshToken());
    RefreshTokenSessionView newestSession = loadRefreshTokenSession(newest.refreshToken());
    RefreshTokenSessionView bobSession = loadRefreshTokenSession(bob.refreshToken());
    assertEquals("REVOKED", oldestSession.sessionStatus());
    assertEquals("REVOKED", middleSession.sessionStatus());
    assertEquals("REVOKED", newestSession.sessionStatus());
    assertNotNull(oldestSession.lastUsedAt());
    assertNotNull(middleSession.lastUsedAt());
    assertNotNull(newestSession.lastUsedAt());
    assertEquals("ACTIVE", bobSession.sessionStatus());

    refreshExpectUnauthorized(oldest.refreshToken(), "session-revoke-all-refresh-001");
    refreshExpectUnauthorized(middle.refreshToken(), "session-revoke-all-refresh-002");
    refreshExpectUnauthorized(newest.refreshToken(), "session-revoke-all-refresh-003");
    refresh(bob.refreshToken(), "session-revoke-all-refresh-004");
  }

  @Test
  void sensitiveMutationsRejectRevokedCurrentSessionAccessToken() throws Exception {
    TokenPairResponseView loginResult =
        loginResult("alice", "password123!", "current-session-gate-login-001");
    long currentSessionId = loadRefreshTokenSessionId(loginResult.refreshToken());

    revokeSession(loginResult.accessToken(), currentSessionId, "current-session-gate-revoke-001")
        .andExpect(status().isNoContent());

    passwordReset(
            loginResult.accessToken(),
            "password123!",
            "newPassword456!",
            "current-session-gate-password-reset-001")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("current session is not active"));

    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("Authorization", "Bearer " + loginResult.accessToken())
                .header("Idempotency-Key", "current-session-gate-transfer-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": %d,
                      "targetAccountId": %d,
                      "amountMinor": 500,
                      "currencyCode": "KRW",
                      "summary": "revoked-session-blocked"
                    }
                    """
                        .formatted(allowedSourceAccountId, targetAccountId)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("current session is not active"));

    performStartTotpEnrollment(loginResult.accessToken(), "current-session-gate-totp-enroll-001")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("current session is not active"));

    revokeAllSessions(loginResult.accessToken(), "current-session-gate-revoke-all-001")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("current session is not active"));
  }

  @Test
  void sensitiveMutationsRejectLegacyJwtWithoutSessionId() throws Exception {
    loginResult("alice", "password123!", "current-session-gate-legacy-login-001");
    String legacyAccessToken = issueLegacyAccessToken("alice", userId);

    passwordReset(
            legacyAccessToken,
            "password123!",
            "newPassword456!",
            "current-session-gate-legacy-password-reset-001")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("current session is not active"));

    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("Authorization", "Bearer " + legacyAccessToken)
                .header("Idempotency-Key", "current-session-gate-legacy-transfer-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": %d,
                      "targetAccountId": %d,
                      "amountMinor": 500,
                      "currencyCode": "KRW",
                      "summary": "legacy-session-blocked"
                    }
                    """
                        .formatted(allowedSourceAccountId, targetAccountId)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("current session is not active"));

    mockMvc
        .perform(
            get("/api/v1/auth/sessions")
                .header("Authorization", "Bearer " + legacyAccessToken)
                .param("size", "10"))
        .andExpect(status().isOk());
  }

  @Test
  void bootstrapAccountPrincipalCannotCallLogout() throws Exception {
    TokenPairResponseView loginResult =
        loginResult("alice", "password123!", "logout-bootstrap-001");

    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .header("X-Account-Id", String.valueOf(allowedSourceAccountId))
                .header("X-Subject", "bootstrap-account")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "refreshToken": "%s"
                    }
                    """
                        .formatted(loginResult.refreshToken())))
        .andExpect(status().isForbidden());
  }

  @Test
  void bootstrapAccountPrincipalCannotRevokeAuthSession() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/auth/sessions/1")
                .header("X-Account-Id", String.valueOf(allowedSourceAccountId))
                .header("X-Subject", "bootstrap-account"))
        .andExpect(status().isForbidden());
  }

  @Test
  void bootstrapAccountPrincipalCannotRevokeAllAuthSessions() throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/auth/sessions")
                .header("X-Account-Id", String.valueOf(allowedSourceAccountId))
                .header("X-Subject", "bootstrap-account"))
        .andExpect(status().isForbidden());
  }

  @Test
  void bootstrapAccountPrincipalCannotGetAuthSessionList() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/auth/sessions")
                .header("X-Account-Id", String.valueOf(allowedSourceAccountId))
                .header("X-Subject", "bootstrap-account"))
        .andExpect(status().isForbidden());
  }

  @Test
  void bootstrapAccountPrincipalCannotResetPassword() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/password-reset")
                .header("X-Account-Id", String.valueOf(allowedSourceAccountId))
                .header("X-Subject", "bootstrap-account")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "currentPassword": "password123!",
                      "newPassword": "newPassword456!"
                    }
                """))
        .andExpect(status().isForbidden());
  }

  @Test
  void bootstrapAccountPrincipalCannotDisableTotp() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/mfa/totp/disable")
                .header("X-Account-Id", String.valueOf(allowedSourceAccountId))
                .header("X-Subject", "bootstrap-account")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "totpCode": "123456"
                    }
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void passwordRecoveryRequestReturnsGenericNoContentWithSeparateHandoffRequestId()
      throws Exception {
    MvcResult existingResult =
        performPasswordRecoveryRequest("alice", "password-recovery-request-existing")
            .andExpect(status().isNoContent())
            .andReturn();
    MvcResult missingResult =
        performPasswordRecoveryRequest("missing-user", "password-recovery-request-missing")
            .andExpect(status().isNoContent())
            .andReturn();

    assertEquals(
        "password-recovery-request-existing",
        existingResult.getResponse().getHeader("X-Request-Id"));
    assertEquals(
        "password-recovery-request-missing", missingResult.getResponse().getHeader("X-Request-Id"));
    String existingHandoffRequestId =
        existingResult.getResponse().getHeader("X-Password-Recovery-Request-Id");
    String missingHandoffRequestId =
        missingResult.getResponse().getHeader("X-Password-Recovery-Request-Id");

    assertNotNull(existingHandoffRequestId);
    assertNotNull(missingHandoffRequestId);
    assertNotEquals("password-recovery-request-existing", existingHandoffRequestId);
    assertNotEquals("password-recovery-request-missing", missingHandoffRequestId);
    assertNotEquals(existingHandoffRequestId, missingHandoffRequestId);

    mockMvc
        .perform(
            get("/internal/api/v1/auth/password-recovery-tokens/by-request-id")
                .header(
                    "Authorization",
                    internalServiceAuthorization(
                        AUTH_ADMIN_SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .param("requestId", missingHandoffRequestId))
        .andExpect(status().isNotFound());
  }

  @Test
  void passwordRecoveryConfirmChangesPasswordAndRevokesRefreshSessions() throws Exception {
    TokenPairResponseView loginResult = loginResult("alice", "password123!", "recovery-login-001");
    MvcResult requestResult =
        performPasswordRecoveryRequest("alice", "recovery-request-001")
            .andExpect(status().isNoContent())
            .andReturn();
    String requestId = requestResult.getResponse().getHeader("X-Password-Recovery-Request-Id");
    JsonNode tokenView = lookupPasswordRecoveryTokenByRequestId(requestId);
    assertEquals("recovery-request-001", requestResult.getResponse().getHeader("X-Request-Id"));
    assertEquals(requestId, tokenView.get("requestId").asText());
    assertEquals("alice", tokenView.get("loginId").asText());
    assertEquals("PENDING", tokenView.get("tokenStatus").asText());

    performPasswordRecoveryConfirm(
            tokenView.get("recoveryToken").asText(), "newPassword456!", "recovery-confirm-001")
        .andExpect(status().isNoContent());

    loginExpectUnauthorized("alice", "password123!", "recovery-old-login-001");
    refreshExpectUnauthorized(loginResult.refreshToken(), "recovery-old-refresh-001");
    login("alice", "newPassword456!", "recovery-new-login-001");
  }

  @Test
  void passwordRecoveryConfirmRejectsWrongTokenWithGenericUnauthorized() throws Exception {
    performPasswordRecoveryConfirm(
            "invalid-recovery-token", "newPassword456!", "recovery-wrong-001")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("password recovery failed"));
  }

  @Test
  void passwordRecoveryConfirmRejectsExpiredTokenWithGenericUnauthorized() throws Exception {
    MvcResult requestResult =
        performPasswordRecoveryRequest("alice", "recovery-expired-request-001")
            .andExpect(status().isNoContent())
            .andReturn();
    String requestId = requestResult.getResponse().getHeader("X-Password-Recovery-Request-Id");
    JsonNode tokenView = lookupPasswordRecoveryTokenByRequestId(requestId);
    expirePasswordRecoveryToken(requestId);
    assertEquals(
        "EXPIRED", lookupPasswordRecoveryTokenByRequestId(requestId).get("tokenStatus").asText());

    performPasswordRecoveryConfirm(
            tokenView.get("recoveryToken").asText(),
            "newPassword456!",
            "recovery-expired-confirm-001")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("password recovery failed"));
  }

  @Test
  void passwordRecoveryConfirmRejectsUsedTokenReuseWithGenericUnauthorized() throws Exception {
    MvcResult requestResult =
        performPasswordRecoveryRequest("alice", "recovery-used-request-001")
            .andExpect(status().isNoContent())
            .andReturn();
    String requestId = requestResult.getResponse().getHeader("X-Password-Recovery-Request-Id");
    JsonNode tokenView = lookupPasswordRecoveryTokenByRequestId(requestId);
    String recoveryToken = tokenView.get("recoveryToken").asText();

    performPasswordRecoveryConfirm(recoveryToken, "newPassword456!", "recovery-used-confirm-001")
        .andExpect(status().isNoContent());

    performPasswordRecoveryConfirm(recoveryToken, "nextPassword789!", "recovery-used-confirm-002")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("password recovery failed"));
  }

  @Test
  void passwordRecoveryConfirmRejectsInactiveUserWithGenericUnauthorized() throws Exception {
    MvcResult requestResult =
        performPasswordRecoveryRequest("alice", "recovery-inactive-request-001")
            .andExpect(status().isNoContent())
            .andReturn();
    String requestId = requestResult.getResponse().getHeader("X-Password-Recovery-Request-Id");
    JsonNode tokenView = lookupPasswordRecoveryTokenByRequestId(requestId);
    updateLegacyUserStatus(userId, "DISABLED", "fraud-review", "recovery-inactive-disable-001");

    performPasswordRecoveryConfirm(
            tokenView.get("recoveryToken").asText(),
            "newPassword456!",
            "recovery-inactive-confirm-001")
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("password recovery failed"));
  }

  @Test
  void logsFailureAndResetWithRequestIdAndMaskedLoginKey(CapturedOutput output) throws Exception {
    loginExpectUnauthorized("alice", "wrong-password", "login-log-failure-001");
    login("alice", "password123!", "login-log-reset-001");

    String logs = output.getOut() + output.getErr();
    org.junit.jupiter.api.Assertions.assertAll(
        () -> org.junit.jupiter.api.Assertions.assertTrue(logs.contains("auth login failed")),
        () ->
            org.junit.jupiter.api.Assertions.assertTrue(
                logs.contains("requestId=login-log-failure-001")),
        () -> org.junit.jupiter.api.Assertions.assertTrue(logs.contains("failureCount=1")),
        () ->
            org.junit.jupiter.api.Assertions.assertTrue(
                logs.contains("reason=INVALID_CREDENTIALS")),
        () -> org.junit.jupiter.api.Assertions.assertTrue(logs.contains("loginIdHash=")),
        () ->
            org.junit.jupiter.api.Assertions.assertTrue(
                logs.contains("auth login failure state reset")),
        () ->
            org.junit.jupiter.api.Assertions.assertTrue(
                logs.contains("requestId=login-log-reset-001")),
        () -> org.junit.jupiter.api.Assertions.assertTrue(logs.contains("previousFailureCount=1")));
  }

  @Test
  void internalAuthAdminLookupReturnsBootstrappedUserAndMembership() throws Exception {
    mockMvc
        .perform(
            get("/internal/api/v1/auth/users/%d".formatted(userId))
                .header(
                    "Authorization",
                    internalServiceAuthorization(
                        AUTH_ADMIN_SUBJECT, InternalServiceScope.AUTH_ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(userId))
        .andExpect(jsonPath("$.loginId").value("alice"))
        .andExpect(jsonPath("$.userStatus").value("ACTIVE"));

    mockMvc
        .perform(
            get("/internal/api/v1/auth/users/by-login-id")
                .header(
                    "Authorization",
                    internalServiceAuthorization(
                        AUTH_ADMIN_SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .param("loginId", "alice"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Alice"));

    mockMvc
        .perform(
            get("/internal/api/v1/auth/users/%d/memberships/%d"
                    .formatted(userId, allowedSourceAccountId))
                .header(
                    "Authorization",
                    internalServiceAuthorization(
                        AUTH_ADMIN_SUBJECT, InternalServiceScope.AUTH_ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(userId))
        .andExpect(jsonPath("$.accountId").value(allowedSourceAccountId))
        .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"));
  }

  @Test
  void disabledUserCannotLoginOrUseExistingJwt() throws Exception {
    TokenPairResponseView loginResult = loginResult("alice", "password123!", null);
    String token = loginResult.accessToken();
    updateLegacyUserStatus(userId, "DISABLED", "fraud-review", "user-disabled-request");

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "loginId": "alice",
                      "password": "password123!"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("login failed"));

    refreshExpectUnauthorized(loginResult.refreshToken(), "refresh-disabled-001");

    mockMvc
        .perform(
            get("/api/v1/transactions")
                .header("Authorization", "Bearer " + token)
                .param("accountId", String.valueOf(allowedSourceAccountId))
                .param("from", transactionQueryFrom())
                .param("to", transactionQueryTo()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));

    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "jwt-transfer-disabled-403")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": %d,
                      "targetAccountId": %d,
                      "amountMinor": 500,
                      "currencyCode": "KRW",
                      "summary": "disabled-blocked"
                    }
                    """
                        .formatted(allowedSourceAccountId, targetAccountId)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("current session is not active"));
  }

  @Test
  void disableRevokesOnlyActiveRefreshSessionsAndOldRefreshStaysBlockedAfterReactivate()
      throws Exception {
    TokenPairResponseView rotatedSource =
        loginResult("alice", "password123!", "disable-session-login-001");
    TokenPairResponseView rotatedTarget =
        refresh(rotatedSource.refreshToken(), "disable-session-rotate-001");
    TokenPairResponseView parallelSession =
        loginResult("alice", "password123!", "disable-session-login-002");

    RefreshTokenSessionView rotatedBeforeDisable =
        loadRefreshTokenSession(rotatedSource.refreshToken());
    RefreshTokenSessionView refreshedBeforeDisable =
        loadRefreshTokenSession(rotatedTarget.refreshToken());
    RefreshTokenSessionView parallelBeforeDisable =
        loadRefreshTokenSession(parallelSession.refreshToken());
    assertEquals("ROTATED", rotatedBeforeDisable.sessionStatus());
    assertEquals("ACTIVE", refreshedBeforeDisable.sessionStatus());
    assertEquals("ACTIVE", parallelBeforeDisable.sessionStatus());

    updateLegacyUserStatus(userId, "DISABLED", "fraud-review", "disable-session-disable-001");

    RefreshTokenSessionView rotatedAfterDisable =
        loadRefreshTokenSession(rotatedSource.refreshToken());
    RefreshTokenSessionView refreshedAfterDisable =
        loadRefreshTokenSession(rotatedTarget.refreshToken());
    RefreshTokenSessionView parallelAfterDisable =
        loadRefreshTokenSession(parallelSession.refreshToken());
    assertEquals("ROTATED", rotatedAfterDisable.sessionStatus());
    assertNotNull(rotatedAfterDisable.rotatedAt());
    assertNotNull(rotatedAfterDisable.replacedBySessionId());
    assertEquals("REVOKED", refreshedAfterDisable.sessionStatus());
    assertNotNull(refreshedAfterDisable.lastUsedAt());
    assertNull(refreshedAfterDisable.rotatedAt());
    assertNull(refreshedAfterDisable.replacedBySessionId());
    assertEquals("REVOKED", parallelAfterDisable.sessionStatus());
    assertNotNull(parallelAfterDisable.lastUsedAt());
    assertNull(parallelAfterDisable.rotatedAt());
    assertNull(parallelAfterDisable.replacedBySessionId());

    updateLegacyUserStatus(userId, "ACTIVE", "manual-reactivate", "disable-session-reactivate-001");

    refreshExpectUnauthorized(rotatedTarget.refreshToken(), "disable-session-old-refresh-001");
    refreshExpectUnauthorized(parallelSession.refreshToken(), "disable-session-old-refresh-002");
  }

  @Test
  void revokedMembershipBlocksExistingJwtAccess() throws Exception {
    String token = login("alice", "password123!");
    updateMembershipStatus(
        userId,
        allowedSourceAccountId,
        "REVOKED",
        "OPS_MANUAL",
        "manual-revoke",
        "membership-revoked-request");

    AuthStatusChangeAuditView audit =
        loadAuditByRequestId("membership-revoked-request")
            .orElseThrow(() -> new AssertionError("audit row is not created"));
    assertEquals("membership-revoked-request", audit.requestId());
    assertEquals(AUTH_ADMIN_SUBJECT, audit.actorSubject());
    assertEquals("MEMBERSHIP_STATUS", audit.changeType());
    assertEquals(userId, audit.targetUserId());
    assertEquals(allowedSourceAccountId, audit.targetAccountId());
    assertEquals("ACTIVE", audit.beforeStatus());
    assertEquals("REVOKED", audit.afterStatus());
    assertEquals("OPS_MANUAL", audit.reasonCode());
    assertEquals("manual-revoke", audit.reasonDetail());
    assertEquals("SUCCESS", audit.outcome());

    mockMvc
        .perform(
            get("/api/v1/transactions")
                .header("Authorization", "Bearer " + token)
                .param("accountId", String.valueOf(allowedSourceAccountId))
                .param("from", transactionQueryFrom())
                .param("to", transactionQueryTo()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));

    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", "jwt-transfer-revoked-403")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": %d,
                      "targetAccountId": %d,
                      "amountMinor": 500,
                      "currencyCode": "KRW",
                      "summary": "revoked-blocked"
                    }
                    """
                        .formatted(allowedSourceAccountId, targetAccountId)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));

    mockMvc
        .perform(get("/api/v1/accounts").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void internalAuthAuditLookupReturnsStoredAuditByRequestId() throws Exception {
    updateMembershipStatus(
        userId,
        allowedSourceAccountId,
        "REVOKED",
        "OPS_MANUAL",
        "manual-revoke",
        "membership-revoked-request");

    AuthStatusChangeAuditView audit =
        loadAuditByRequestId("membership-revoked-request")
            .orElseThrow(() -> new AssertionError("audit row is not created"));

    mockMvc
        .perform(
            get("/internal/api/v1/auth/status-change-audits/by-request-id")
                .header(
                    "Authorization",
                    internalServiceAuthorization(
                        AUTH_ADMIN_SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .param("requestId", "membership-revoked-request"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestId").value(audit.requestId()))
        .andExpect(jsonPath("$.actorSubject").value(audit.actorSubject()))
        .andExpect(jsonPath("$.targetUserId").value(audit.targetUserId()))
        .andExpect(jsonPath("$.targetAccountId").value(audit.targetAccountId()))
        .andExpect(jsonPath("$.changeType").value(audit.changeType()))
        .andExpect(jsonPath("$.beforeStatus").value(audit.beforeStatus()))
        .andExpect(jsonPath("$.afterStatus").value(audit.afterStatus()))
        .andExpect(jsonPath("$.reasonCode").value(audit.reasonCode()))
        .andExpect(jsonPath("$.reasonDetail").value(audit.reasonDetail()))
        .andExpect(jsonPath("$.reason").value(audit.reasonDetail()))
        .andExpect(jsonPath("$.outcome").value(audit.outcome()))
        .andExpect(jsonPath("$.createdAt").value(audit.createdAt().toString()));
  }

  @Test
  void legacyReasonRequestFallsBackToLegacyFreeTextCode() throws Exception {
    updateLegacyUserStatus(userId, "DISABLED", "fraud-review", "legacy-reason-request");

    AuthStatusChangeAuditView audit =
        loadAuditByRequestId("legacy-reason-request")
            .orElseThrow(() -> new AssertionError("audit row is not created"));

    assertEquals("LEGACY_FREE_TEXT", audit.reasonCode());
    assertEquals("fraud-review", audit.reasonDetail());
  }

  @Test
  void legacyMembershipReasonRequestFallsBackToLegacyFreeTextCode() throws Exception {
    updateLegacyMembershipStatus(
        userId,
        allowedSourceAccountId,
        "REVOKED",
        "manual-revoke",
        "membership-legacy-reason-request");

    AuthStatusChangeAuditView audit =
        loadAuditByRequestId("membership-legacy-reason-request")
            .orElseThrow(() -> new AssertionError("audit row is not created"));

    assertEquals("membership-legacy-reason-request", audit.requestId());
    assertEquals("LEGACY_FREE_TEXT", audit.reasonCode());
    assertEquals("manual-revoke", audit.reasonDetail());
    assertEquals("SUCCESS", audit.outcome());
  }

  @Test
  void totpEnrollmentActivatesCredentialAndStoresProtectedSecret() throws Exception {
    TokenPairResponseView loginResult =
        loginResult("alice", "password123!", "totp-enroll-login-001", WINDOWS_CHROME);

    TotpEnrollmentStartResponseView enrollment =
        startTotpEnrollment(loginResult.accessToken(), "totp-enroll-start-001");
    TotpCredentialView pendingCredential = loadTotpCredential(userId);

    assertEquals("PENDING", enrollment.status());
    assertTrue(enrollment.otpauthUri().contains("otpauth://totp/"));
    assertEquals("PENDING", pendingCredential.credentialStatus());
    assertNotEquals(enrollment.secretKey(), pendingCredential.secretCiphertext());
    assertNotNull(pendingCredential.pendingExpiresAt());
    assertNull(pendingCredential.verifiedAt());

    TotpEnrollmentVerifyResponseView verified =
        verifyTotpEnrollment(
            loginResult.accessToken(),
            currentTotpCode(enrollment.secretKey()),
            "totp-enroll-verify-001");
    TotpCredentialView activeCredential = loadTotpCredential(userId);

    assertEquals("ACTIVE", verified.status());
    assertEquals("ACTIVE", activeCredential.credentialStatus());
    assertNotNull(activeCredential.verifiedAt());
  }

  @Test
  void mfaActiveUserLoginReturnsChallengeAndVerifyIssuesTokenPair() throws Exception {
    TokenPairResponseView initialSession =
        loginResult("alice", "password123!", "totp-mfa-login-001", WINDOWS_CHROME);
    TotpEnrollmentStartResponseView enrollment =
        startTotpEnrollment(initialSession.accessToken(), "totp-mfa-start-001");
    verifyTotpEnrollment(
        initialSession.accessToken(),
        currentTotpCode(enrollment.secretKey()),
        "totp-mfa-verify-001");

    LoginChallengeResponseView challenge =
        loginChallenge("alice", "password123!", "totp-mfa-login-002", WINDOWS_EDGE);

    assertEquals("MFA_REQUIRED", challenge.status());
    assertEquals("TOTP", challenge.challengeType());
    assertEquals(1, countActiveRefreshSessions(userId));

    TokenPairResponseView verifiedSession =
        verifyTotpChallenge(
            challenge.challengeId(),
            currentTotpCode(enrollment.secretKey()),
            "totp-mfa-challenge-verify-001",
            IPHONE_SAFARI);
    RefreshTokenSessionView challengeSession =
        loadRefreshTokenSession(verifiedSession.refreshToken());
    TotpLoginChallengeView verifiedChallenge = loadTotpLoginChallenge(challenge.challengeId());

    assertEquals("SUCCESS", verifiedSession.status());
    assertEquals("Windows / Edge", challengeSession.deviceName());
    assertEquals("192.0.2.77", challengeSession.ipAddress());
    assertEquals("VERIFIED", verifiedChallenge.challengeStatus());
    assertEquals(2, countActiveRefreshSessions(userId));

    verifyTotpChallengeExpectUnauthorized(
        challenge.challengeId(),
        currentTotpCode(enrollment.secretKey()),
        "totp-mfa-challenge-reuse-001");
  }

  @Test
  void rememberDeviceLoginBypassesMfaAndRotatesCookie() throws Exception {
    RememberDeviceSessionView rememberedSession =
        enableRememberDevice("remember-device-bypass", WINDOWS_EDGE);

    assertEquals(1, countRememberDevicesByStatus(userId, "ACTIVE"));
    assertEquals(
        1, countActiveRememberDevicesByToken(userId, rememberedSession.rememberDeviceToken()));

    MvcResult bypassResult =
        performLogin(
                "alice",
                "password123!",
                "remember-device-bypass-login-003",
                IPHONE_SAFARI,
                rememberedSession.rememberDeviceToken())
            .andExpect(status().isOk())
            .andReturn();
    TokenPairResponseView bypassedSession = readTokenPair(bypassResult);
    String rotatedRememberDeviceToken = readRememberDeviceToken(bypassResult);
    RefreshTokenSessionView bypassedRefreshSession =
        loadRefreshTokenSession(bypassedSession.refreshToken());

    assertEquals("SUCCESS", bypassedSession.status());
    assertNotEquals(rememberedSession.rememberDeviceToken(), rotatedRememberDeviceToken);
    assertEquals(1, countRememberDevicesByStatus(userId, "ACTIVE"));
    assertEquals(
        0, countActiveRememberDevicesByToken(userId, rememberedSession.rememberDeviceToken()));
    assertEquals(1, countActiveRememberDevicesByToken(userId, rotatedRememberDeviceToken));
    assertEquals(IPHONE_SAFARI.expectedDeviceName(), bypassedRefreshSession.deviceName());
    assertEquals(IPHONE_SAFARI.expectedIpAddress(), bypassedRefreshSession.ipAddress());
  }

  @Test
  void logoutRevokesRememberDeviceAndStaleCookieFallsBackToMfa() throws Exception {
    RememberDeviceSessionView rememberedSession =
        enableRememberDevice("remember-device-logout", WINDOWS_EDGE);

    MvcResult logoutResult =
        logout(
                rememberedSession.session().accessToken(),
                rememberedSession.session().refreshToken(),
                "remember-device-logout-request-001",
                rememberedSession.rememberDeviceToken())
            .andExpect(status().isNoContent())
            .andReturn();

    assertRememberDeviceCleared(logoutResult);
    assertEquals(0, countRememberDevicesByStatus(userId, "ACTIVE"));
    assertEquals(1, countRememberDevicesByStatus(userId, "REVOKED"));
    assertEquals(
        0, countActiveRememberDevicesByToken(userId, rememberedSession.rememberDeviceToken()));

    MvcResult loginResult =
        performLogin(
                "alice",
                "password123!",
                "remember-device-logout-login-003",
                WINDOWS_CHROME,
                rememberedSession.rememberDeviceToken())
            .andExpect(status().isOk())
            .andReturn();
    LoginChallengeResponseView challenge = readLoginChallenge(loginResult);

    assertEquals("TOTP", challenge.challengeType());
    assertRememberDeviceCleared(loginResult);
  }

  @Test
  void revokeAllSessionsRevokesRememberDeviceAndStaleCookieFallsBackToMfa() throws Exception {
    RememberDeviceSessionView rememberedSession =
        enableRememberDevice("remember-device-revoke-all", WINDOWS_EDGE);

    MvcResult revokeResult =
        revokeAllSessions(
                rememberedSession.session().accessToken(),
                "remember-device-revoke-all-request-001",
                rememberedSession.rememberDeviceToken())
            .andExpect(status().isNoContent())
            .andReturn();

    assertRememberDeviceCleared(revokeResult);
    assertEquals(0, countRememberDevicesByStatus(userId, "ACTIVE"));
    assertEquals(1, countRememberDevicesByStatus(userId, "REVOKED"));

    MvcResult loginResult =
        performLogin(
                "alice",
                "password123!",
                "remember-device-revoke-all-login-003",
                WINDOWS_CHROME,
                rememberedSession.rememberDeviceToken())
            .andExpect(status().isOk())
            .andReturn();
    LoginChallengeResponseView challenge = readLoginChallenge(loginResult);

    assertEquals("TOTP", challenge.challengeType());
    assertRememberDeviceCleared(loginResult);
  }

  @Test
  void passwordResetRevokesRememberDeviceAndStaleCookieFallsBackToMfa() throws Exception {
    RememberDeviceSessionView rememberedSession =
        enableRememberDevice("remember-device-password-reset", WINDOWS_EDGE);

    MvcResult resetResult =
        passwordReset(
                rememberedSession.session().accessToken(),
                "password123!",
                "newPassword456!",
                "remember-device-password-reset-request-001",
                rememberedSession.rememberDeviceToken())
            .andExpect(status().isNoContent())
            .andReturn();

    assertRememberDeviceCleared(resetResult);
    assertEquals(0, countRememberDevicesByStatus(userId, "ACTIVE"));
    assertEquals(1, countRememberDevicesByStatus(userId, "REVOKED"));

    MvcResult loginResult =
        performLogin(
                "alice",
                "newPassword456!",
                "remember-device-password-reset-login-003",
                WINDOWS_CHROME,
                rememberedSession.rememberDeviceToken())
            .andExpect(status().isOk())
            .andReturn();
    LoginChallengeResponseView challenge = readLoginChallenge(loginResult);

    assertEquals("TOTP", challenge.challengeType());
    assertRememberDeviceCleared(loginResult);
  }

  @Test
  void backupCodeIssueAndChallengeVerifyConsumeCode() throws Exception {
    TokenPairResponseView initialSession =
        loginResult("alice", "password123!", "backup-code-login-001", WINDOWS_CHROME);
    TotpEnrollmentStartResponseView enrollment =
        startTotpEnrollment(initialSession.accessToken(), "backup-code-start-001");
    verifyTotpEnrollment(
        initialSession.accessToken(),
        currentTotpCode(enrollment.secretKey()),
        "backup-code-enroll-verify-001");

    BackupCodeIssueResponseView issued =
        issueBackupCodes(
            initialSession.accessToken(),
            currentTotpCode(enrollment.secretKey()),
            "backup-code-issue-001");

    assertEquals(10, issued.codeCount());
    assertEquals(10, issued.backupCodes().size());
    assertEquals(10, countBackupCodesByStatus(userId, "ACTIVE"));
    assertEquals(0, countBackupCodeRowsMatchingPlainValue(issued.backupCodes().get(0)));

    LoginChallengeResponseView challenge =
        loginChallenge("alice", "password123!", "backup-code-login-002", WINDOWS_EDGE);

    TokenPairResponseView verifiedSession =
        verifyBackupCodeChallenge(
            challenge.challengeId(),
            issued.backupCodes().get(0),
            "backup-code-challenge-verify-001",
            IPHONE_SAFARI);
    RefreshTokenSessionView challengeSession =
        loadRefreshTokenSession(verifiedSession.refreshToken());
    TotpLoginChallengeView verifiedChallenge = loadTotpLoginChallenge(challenge.challengeId());

    assertEquals("SUCCESS", verifiedSession.status());
    assertEquals("Windows / Edge", challengeSession.deviceName());
    assertEquals("192.0.2.77", challengeSession.ipAddress());
    assertEquals("VERIFIED", verifiedChallenge.challengeStatus());
    assertEquals(9, countBackupCodesByStatus(userId, "ACTIVE"));
    assertEquals(1, countBackupCodesByStatus(userId, "USED"));

    verifyBackupCodeChallengeExpectUnauthorized(
        challenge.challengeId(), issued.backupCodes().get(0), "backup-code-reuse-001");
  }

  @Test
  void backupCodeReissueSupersedesPreviousSet() throws Exception {
    TokenPairResponseView initialSession =
        loginResult("alice", "password123!", "backup-reissue-login-001", WINDOWS_CHROME);
    TotpEnrollmentStartResponseView enrollment =
        startTotpEnrollment(initialSession.accessToken(), "backup-reissue-start-001");
    verifyTotpEnrollment(
        initialSession.accessToken(),
        currentTotpCode(enrollment.secretKey()),
        "backup-reissue-enroll-verify-001");

    BackupCodeIssueResponseView firstIssued =
        issueBackupCodes(
            initialSession.accessToken(),
            currentTotpCode(enrollment.secretKey()),
            "backup-reissue-issue-001");
    BackupCodeIssueResponseView secondIssued =
        issueBackupCodes(
            initialSession.accessToken(),
            currentTotpCode(enrollment.secretKey()),
            "backup-reissue-issue-002");

    assertEquals(10, countBackupCodesByStatus(userId, "ACTIVE"));
    assertEquals(10, countBackupCodesByStatus(userId, "SUPERSEDED"));

    LoginChallengeResponseView failedChallenge =
        loginChallenge("alice", "password123!", "backup-reissue-login-002", WINDOWS_EDGE);
    verifyBackupCodeChallengeExpectUnauthorized(
        failedChallenge.challengeId(),
        firstIssued.backupCodes().get(0),
        "backup-reissue-old-code-001");

    LoginChallengeResponseView successChallenge =
        loginChallenge("alice", "password123!", "backup-reissue-login-003", WINDOWS_CHROME);
    verifyBackupCodeChallenge(
        successChallenge.challengeId(),
        secondIssued.backupCodes().get(0),
        "backup-reissue-new-code-001",
        WINDOWS_EDGE);

    assertEquals(9, countBackupCodesByStatus(userId, "ACTIVE"));
    assertEquals(1, countBackupCodesByStatus(userId, "USED"));
    assertEquals(10, countBackupCodesByStatus(userId, "SUPERSEDED"));
  }

  @Test
  void totpChallengeStopsAfterMaxAttempts() throws Exception {
    TokenPairResponseView initialSession =
        loginResult("alice", "password123!", "totp-limit-login-001", WINDOWS_CHROME);
    TotpEnrollmentStartResponseView enrollment =
        startTotpEnrollment(initialSession.accessToken(), "totp-limit-start-001");
    verifyTotpEnrollment(
        initialSession.accessToken(),
        currentTotpCode(enrollment.secretKey()),
        "totp-limit-verify-001");

    LoginChallengeResponseView challenge =
        loginChallenge("alice", "password123!", "totp-limit-login-002", WINDOWS_CHROME);

    for (int attempt = 1; attempt <= 5; attempt++) {
      verifyTotpChallengeExpectUnauthorized(
          challenge.challengeId(), "000000", "totp-limit-challenge-attempt-%d".formatted(attempt));
    }

    TotpLoginChallengeView failedChallenge = loadTotpLoginChallenge(challenge.challengeId());
    assertEquals("FAILED", failedChallenge.challengeStatus());
    assertEquals(5, failedChallenge.attemptCount());

    verifyTotpChallengeExpectUnauthorized(
        challenge.challengeId(),
        currentTotpCode(enrollment.secretKey()),
        "totp-limit-challenge-after-failed");
  }

  @Test
  void totpDisableDeletesCredentialRevokesSessionsAndNextLoginReturnsTokenPair() throws Exception {
    TokenPairResponseView initialSession =
        loginResult("alice", "password123!", "totp-disable-login-001", WINDOWS_CHROME);
    TotpEnrollmentStartResponseView enrollment =
        startTotpEnrollment(initialSession.accessToken(), "totp-disable-start-001");
    verifyTotpEnrollment(
        initialSession.accessToken(),
        currentTotpCode(enrollment.secretKey()),
        "totp-disable-verify-001");
    issueBackupCodes(
        initialSession.accessToken(),
        currentTotpCode(enrollment.secretKey()),
        "totp-disable-backup-issue-001");

    disableTotp(
        initialSession.accessToken(),
        currentTotpCode(enrollment.secretKey()),
        "totp-disable-request-001");

    assertEquals(0, countTotpCredentialRows(userId));
    assertEquals(0, countBackupCodesByStatus(userId, "ACTIVE"));
    assertEquals(10, countBackupCodesByStatus(userId, "SUPERSEDED"));
    assertEquals(0, countActiveRefreshSessions(userId));

    loginResult("alice", "password123!", "totp-disable-login-002", WINDOWS_EDGE);
  }

  @Test
  void totpDisableRevokesRememberDeviceAndClearsCookie() throws Exception {
    RememberDeviceSessionView rememberedSession =
        enableRememberDevice("remember-device-disable", WINDOWS_EDGE);

    MvcResult disableResult =
        performDisableTotp(
                rememberedSession.session().accessToken(),
                currentTotpCode(rememberedSession.secretKey()),
                "remember-device-disable-request-001",
                rememberedSession.rememberDeviceToken())
            .andExpect(status().isNoContent())
            .andReturn();

    assertRememberDeviceCleared(disableResult);
    assertEquals(0, countRememberDevicesByStatus(userId, "ACTIVE"));
    assertEquals(1, countRememberDevicesByStatus(userId, "REVOKED"));
  }

  @Test
  void totpDisableRejectsWrongCodeAndKeepsCredential() throws Exception {
    TokenPairResponseView initialSession =
        loginResult("alice", "password123!", "totp-disable-wrong-login-001", WINDOWS_CHROME);
    TotpEnrollmentStartResponseView enrollment =
        startTotpEnrollment(initialSession.accessToken(), "totp-disable-wrong-start-001");
    verifyTotpEnrollment(
        initialSession.accessToken(),
        currentTotpCode(enrollment.secretKey()),
        "totp-disable-wrong-verify-001");

    disableTotpExpectUnauthorized(
        initialSession.accessToken(), "000000", "totp-disable-wrong-request-001");

    assertEquals(1, countTotpCredentialRows(userId));
    assertEquals(1, countActiveRefreshSessions(userId));
  }

  private String login(String loginId, String password) throws Exception {
    return login(loginId, password, null);
  }

  private String transactionReference(MvcResult result) throws Exception {
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    return body.get("transactionReference").asText();
  }

  private String login(String loginId, String password, String requestId) throws Exception {
    return loginResult(loginId, password, requestId).accessToken();
  }

  private TokenPairResponseView loginResult(String loginId, String password, String requestId)
      throws Exception {
    MvcResult result =
        performLogin(loginId, password, requestId).andExpect(status().isOk()).andReturn();
    return readTokenPair(result);
  }

  private TokenPairResponseView loginResult(
      String loginId, String password, String requestId, RequestClientMetadata clientMetadata)
      throws Exception {
    MvcResult result =
        performLogin(loginId, password, requestId, clientMetadata)
            .andExpect(status().isOk())
            .andReturn();
    return readTokenPair(result);
  }

  private LoginChallengeResponseView loginChallenge(
      String loginId, String password, String requestId, RequestClientMetadata clientMetadata)
      throws Exception {
    MvcResult result =
        performLogin(loginId, password, requestId, clientMetadata)
            .andExpect(status().isOk())
            .andReturn();
    return readLoginChallenge(result);
  }

  private void loginExpectUnauthorized(String loginId, String password, String requestId)
      throws Exception {
    performLogin(loginId, password, requestId)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("login failed"));
  }

  private org.springframework.test.web.servlet.ResultActions performLogin(
      String loginId, String password, String requestId) throws Exception {
    return performLogin(loginId, password, requestId, null, null);
  }

  private org.springframework.test.web.servlet.ResultActions performLogin(
      String loginId, String password, String requestId, RequestClientMetadata clientMetadata)
      throws Exception {
    return performLogin(loginId, password, requestId, clientMetadata, null);
  }

  private org.springframework.test.web.servlet.ResultActions performLogin(
      String loginId,
      String password,
      String requestId,
      RequestClientMetadata clientMetadata,
      String rememberDeviceToken)
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
    applyClientMetadata(requestBuilder, clientMetadata);
    applyRememberDeviceCookie(requestBuilder, rememberDeviceToken);
    return mockMvc.perform(requestBuilder);
  }

  private TokenPairResponseView refresh(String refreshToken, String requestId) throws Exception {
    MvcResult result =
        performRefresh(refreshToken, requestId).andExpect(status().isOk()).andReturn();
    return readTokenPair(result);
  }

  private TokenPairResponseView refresh(
      String refreshToken, String requestId, RequestClientMetadata clientMetadata)
      throws Exception {
    MvcResult result =
        performRefresh(refreshToken, requestId, clientMetadata)
            .andExpect(status().isOk())
            .andReturn();
    return readTokenPair(result);
  }

  private void refreshExpectUnauthorized(String refreshToken, String requestId) throws Exception {
    performRefresh(refreshToken, requestId)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("refresh failed"));
  }

  private org.springframework.test.web.servlet.ResultActions performRefresh(
      String refreshToken, String requestId) throws Exception {
    return performRefresh(refreshToken, requestId, null);
  }

  private org.springframework.test.web.servlet.ResultActions performRefresh(
      String refreshToken, String requestId, RequestClientMetadata clientMetadata)
      throws Exception {
    MockHttpServletRequestBuilder requestBuilder =
        post("/api/v1/auth/refresh")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "refreshToken": "%s"
                }
                """
                    .formatted(refreshToken));
    if (requestId != null && !requestId.isBlank()) {
      requestBuilder.header("X-Request-Id", requestId);
    }
    String refreshDeviceBindingToken = refreshDeviceBindingTokenByRefreshToken.get(refreshToken);
    if (refreshDeviceBindingToken != null) {
      requestBuilder.cookie(new Cookie(REFRESH_DEVICE_COOKIE_NAME, refreshDeviceBindingToken));
    }
    applyClientMetadata(requestBuilder, clientMetadata);
    return mockMvc.perform(requestBuilder);
  }

  private TotpEnrollmentStartResponseView startTotpEnrollment(String accessToken, String requestId)
      throws Exception {
    MvcResult result =
        performStartTotpEnrollment(accessToken, requestId).andExpect(status().isOk()).andReturn();
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    return new TotpEnrollmentStartResponseView(
        body.get("status").asText(),
        body.get("secretKey").asText(),
        body.get("otpauthUri").asText(),
        Instant.parse(body.get("expiresAt").asText()));
  }

  private TotpEnrollmentVerifyResponseView verifyTotpEnrollment(
      String accessToken, String totpCode, String requestId) throws Exception {
    MvcResult result =
        performVerifyTotpEnrollment(accessToken, totpCode, requestId)
            .andExpect(status().isOk())
            .andReturn();
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    return new TotpEnrollmentVerifyResponseView(
        body.get("status").asText(), Instant.parse(body.get("verifiedAt").asText()));
  }

  private TokenPairResponseView verifyTotpChallenge(
      String challengeId, String totpCode, String requestId, RequestClientMetadata clientMetadata)
      throws Exception {
    MvcResult result =
        performVerifyTotpChallenge(challengeId, totpCode, requestId, clientMetadata, null)
            .andExpect(status().isOk())
            .andReturn();
    return readTokenPair(result);
  }

  private void verifyTotpChallengeExpectUnauthorized(
      String challengeId, String totpCode, String requestId) throws Exception {
    performVerifyTotpChallenge(challengeId, totpCode, requestId, null, null)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("mfa challenge failed"));
  }

  private BackupCodeIssueResponseView issueBackupCodes(
      String accessToken, String totpCode, String requestId) throws Exception {
    MvcResult result =
        performIssueBackupCodes(accessToken, totpCode, requestId)
            .andExpect(status().isOk())
            .andReturn();
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    List<String> backupCodes = new java.util.ArrayList<>();
    for (JsonNode item : body.get("backupCodes")) {
      backupCodes.add(item.asText());
    }
    return new BackupCodeIssueResponseView(body.get("codeCount").asInt(), backupCodes);
  }

  private TokenPairResponseView verifyBackupCodeChallenge(
      String challengeId, String backupCode, String requestId, RequestClientMetadata clientMetadata)
      throws Exception {
    MvcResult result =
        performVerifyBackupCodeChallenge(challengeId, backupCode, requestId, clientMetadata)
            .andReturn();
    assertEquals(200, result.getResponse().getStatus(), result.getResponse().getContentAsString());
    return readTokenPair(result);
  }

  private void verifyBackupCodeChallengeExpectUnauthorized(
      String challengeId, String backupCode, String requestId) throws Exception {
    performVerifyBackupCodeChallenge(challengeId, backupCode, requestId, null)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("mfa challenge failed"));
  }

  private void disableTotp(String accessToken, String totpCode, String requestId) throws Exception {
    performDisableTotp(accessToken, totpCode, requestId).andExpect(status().isNoContent());
  }

  private void disableTotpExpectUnauthorized(String accessToken, String totpCode, String requestId)
      throws Exception {
    performDisableTotp(accessToken, totpCode, requestId)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("mfa disable failed"));
  }

  private org.springframework.test.web.servlet.ResultActions performStartTotpEnrollment(
      String accessToken, String requestId) throws Exception {
    MockHttpServletRequestBuilder requestBuilder =
        post("/api/v1/auth/mfa/totp/enroll").header("Authorization", "Bearer " + accessToken);
    if (requestId != null && !requestId.isBlank()) {
      requestBuilder.header("X-Request-Id", requestId);
    }
    return mockMvc.perform(requestBuilder);
  }

  private org.springframework.test.web.servlet.ResultActions performVerifyTotpEnrollment(
      String accessToken, String totpCode, String requestId) throws Exception {
    MockHttpServletRequestBuilder requestBuilder =
        post("/api/v1/auth/mfa/totp/enroll/verify")
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "totpCode": "%s"
                }
                """
                    .formatted(totpCode));
    if (requestId != null && !requestId.isBlank()) {
      requestBuilder.header("X-Request-Id", requestId);
    }
    return mockMvc.perform(requestBuilder);
  }

  private org.springframework.test.web.servlet.ResultActions performVerifyTotpChallenge(
      String challengeId, String totpCode, String requestId, RequestClientMetadata clientMetadata)
      throws Exception {
    return performVerifyTotpChallenge(challengeId, totpCode, requestId, clientMetadata, null);
  }

  private org.springframework.test.web.servlet.ResultActions performVerifyTotpChallenge(
      String challengeId,
      String totpCode,
      String requestId,
      RequestClientMetadata clientMetadata,
      Boolean rememberDevice)
      throws Exception {
    String rememberDeviceField =
        rememberDevice == null ? "" : ",\n  \"rememberDevice\": " + rememberDevice;
    MockHttpServletRequestBuilder requestBuilder =
        post("/api/v1/auth/mfa/totp/challenge/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "challengeId": "%s",
                  "totpCode": "%s"%s
                }
                """
                    .formatted(challengeId, totpCode, rememberDeviceField));
    if (requestId != null && !requestId.isBlank()) {
      requestBuilder.header("X-Request-Id", requestId);
    }
    applyClientMetadata(requestBuilder, clientMetadata);
    return mockMvc.perform(requestBuilder);
  }

  private org.springframework.test.web.servlet.ResultActions performDisableTotp(
      String accessToken, String totpCode, String requestId) throws Exception {
    return performDisableTotp(accessToken, totpCode, requestId, null);
  }

  private org.springframework.test.web.servlet.ResultActions performDisableTotp(
      String accessToken, String totpCode, String requestId, String rememberDeviceToken)
      throws Exception {
    MockHttpServletRequestBuilder requestBuilder =
        post("/api/v1/auth/mfa/totp/disable")
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "totpCode": "%s"
                }
                """
                    .formatted(totpCode));
    if (requestId != null && !requestId.isBlank()) {
      requestBuilder.header("X-Request-Id", requestId);
    }
    applyRememberDeviceCookie(requestBuilder, rememberDeviceToken);
    return mockMvc.perform(requestBuilder);
  }

  private org.springframework.test.web.servlet.ResultActions performIssueBackupCodes(
      String accessToken, String totpCode, String requestId) throws Exception {
    MockHttpServletRequestBuilder requestBuilder =
        post("/api/v1/auth/mfa/backup-codes")
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "totpCode": "%s"
                }
                """
                    .formatted(totpCode));
    if (requestId != null && !requestId.isBlank()) {
      requestBuilder.header("X-Request-Id", requestId);
    }
    return mockMvc.perform(requestBuilder);
  }

  private org.springframework.test.web.servlet.ResultActions performVerifyBackupCodeChallenge(
      String challengeId, String backupCode, String requestId, RequestClientMetadata clientMetadata)
      throws Exception {
    MockHttpServletRequestBuilder requestBuilder =
        post("/api/v1/auth/mfa/backup-codes/challenge/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "challengeId": "%s",
                  "backupCode": "%s"
                }
                """
                    .formatted(challengeId, backupCode));
    if (requestId != null && !requestId.isBlank()) {
      requestBuilder.header("X-Request-Id", requestId);
    }
    applyClientMetadata(requestBuilder, clientMetadata);
    return mockMvc.perform(requestBuilder);
  }

  private void applyClientMetadata(
      MockHttpServletRequestBuilder requestBuilder, RequestClientMetadata clientMetadata) {
    if (clientMetadata == null) {
      return;
    }
    requestBuilder.header("User-Agent", clientMetadata.userAgent());
    requestBuilder.header("X-Forwarded-For", clientMetadata.forwardedFor());
  }

  private void applyRememberDeviceCookie(
      MockHttpServletRequestBuilder requestBuilder, String rememberDeviceToken) {
    if (rememberDeviceToken == null || rememberDeviceToken.isBlank()) {
      return;
    }
    requestBuilder.cookie(new Cookie(REMEMBER_DEVICE_COOKIE_NAME, rememberDeviceToken));
  }

  private org.springframework.test.web.servlet.ResultActions logout(
      String accessToken, String refreshToken, String requestId) throws Exception {
    return logout(accessToken, refreshToken, requestId, null);
  }

  private org.springframework.test.web.servlet.ResultActions logout(
      String accessToken, String refreshToken, String requestId, String rememberDeviceToken)
      throws Exception {
    MockHttpServletRequestBuilder requestBuilder =
        post("/api/v1/auth/logout")
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "refreshToken": "%s"
                }
                """
                    .formatted(refreshToken));
    if (requestId != null && !requestId.isBlank()) {
      requestBuilder.header("X-Request-Id", requestId);
    }
    applyRememberDeviceCookie(requestBuilder, rememberDeviceToken);
    return mockMvc.perform(requestBuilder);
  }

  private org.springframework.test.web.servlet.ResultActions passwordReset(
      String accessToken, String currentPassword, String newPassword, String requestId)
      throws Exception {
    return passwordReset(accessToken, currentPassword, newPassword, requestId, null);
  }

  private org.springframework.test.web.servlet.ResultActions passwordReset(
      String accessToken,
      String currentPassword,
      String newPassword,
      String requestId,
      String rememberDeviceToken)
      throws Exception {
    MockHttpServletRequestBuilder requestBuilder =
        post("/api/v1/auth/password-reset")
            .header("Authorization", "Bearer " + accessToken)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "currentPassword": "%s",
                  "newPassword": "%s"
                }
                """
                    .formatted(currentPassword, newPassword));
    if (requestId != null && !requestId.isBlank()) {
      requestBuilder.header("X-Request-Id", requestId);
    }
    applyRememberDeviceCookie(requestBuilder, rememberDeviceToken);
    return mockMvc.perform(requestBuilder);
  }

  private org.springframework.test.web.servlet.ResultActions performPasswordRecoveryRequest(
      String loginId, String requestId) throws Exception {
    MockHttpServletRequestBuilder requestBuilder =
        post("/api/v1/auth/password-recovery/request")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "loginId": "%s"
                }
                """
                    .formatted(loginId));
    if (requestId != null && !requestId.isBlank()) {
      requestBuilder.header("X-Request-Id", requestId);
    }
    return mockMvc.perform(requestBuilder);
  }

  private org.springframework.test.web.servlet.ResultActions performPasswordRecoveryConfirm(
      String recoveryToken, String newPassword, String requestId) throws Exception {
    MockHttpServletRequestBuilder requestBuilder =
        post("/api/v1/auth/password-recovery/confirm")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {
                  "recoveryToken": "%s",
                  "newPassword": "%s"
                }
                """
                    .formatted(recoveryToken, newPassword));
    if (requestId != null && !requestId.isBlank()) {
      requestBuilder.header("X-Request-Id", requestId);
    }
    return mockMvc.perform(requestBuilder);
  }

  private JsonNode lookupPasswordRecoveryTokenByRequestId(String requestId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/internal/api/v1/auth/password-recovery-tokens/by-request-id")
                    .header(
                        "Authorization",
                        internalServiceAuthorization(
                            AUTH_ADMIN_SUBJECT, InternalServiceScope.AUTH_ADMIN))
                    .param("requestId", requestId))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsByteArray());
  }

  private void expirePasswordRecoveryToken(String requestId) {
    commit(
        transactionManager,
        () -> {
          int updated =
              jdbcTemplate.update(
                  """
                  UPDATE auth_password_recovery_token
                  SET token_status = 'EXPIRED',
                      expires_at = CURRENT_TIMESTAMP - INTERVAL '1 minute',
                      updated_at = CURRENT_TIMESTAMP
                  WHERE request_id = :requestId
                  """,
                  new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                      .addValue("requestId", requestId));
          assertEquals(1, updated);
        });
  }

  private void passwordResetExpectUnauthorized(
      String accessToken, String currentPassword, String newPassword, String requestId)
      throws Exception {
    passwordReset(accessToken, currentPassword, newPassword, requestId)
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("password reset failed"));
  }

  private org.springframework.test.web.servlet.ResultActions revokeSession(
      String accessToken, long sessionId, String requestId) throws Exception {
    MockHttpServletRequestBuilder requestBuilder =
        delete("/api/v1/auth/sessions/{sessionId}", sessionId)
            .header("Authorization", "Bearer " + accessToken);
    if (requestId != null && !requestId.isBlank()) {
      requestBuilder.header("X-Request-Id", requestId);
    }
    return mockMvc.perform(requestBuilder);
  }

  private org.springframework.test.web.servlet.ResultActions revokeAllSessions(
      String accessToken, String requestId) throws Exception {
    return revokeAllSessions(accessToken, requestId, null);
  }

  private org.springframework.test.web.servlet.ResultActions revokeAllSessions(
      String accessToken, String requestId, String rememberDeviceToken) throws Exception {
    MockHttpServletRequestBuilder requestBuilder =
        delete("/api/v1/auth/sessions").header("Authorization", "Bearer " + accessToken);
    if (requestId != null && !requestId.isBlank()) {
      requestBuilder.header("X-Request-Id", requestId);
    }
    applyRememberDeviceCookie(requestBuilder, rememberDeviceToken);
    return mockMvc.perform(requestBuilder);
  }

  private long bootstrapAccount(String displayName, long initialBalanceMinor) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/internal/api/v1/accounts/bootstrap")
                    .header(
                        "Authorization",
                        internalServiceAuthorization(
                            "account-bootstrap", InternalServiceScope.ACCOUNT_BOOTSTRAP))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "displayName": "%s",
                          "currencyCode": "KRW",
                          "initialBalanceMinor": %d
                        }
                        """
                            .formatted(displayName, initialBalanceMinor)))
            .andExpect(status().isOk())
            .andReturn();

    return objectMapper
        .readTree(result.getResponse().getContentAsByteArray())
        .get("accountId")
        .asLong();
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

  private void upsertMembership(
      long userId, long accountId, String membershipRole, String membershipStatus)
      throws Exception {
    mockMvc
        .perform(
            put("/internal/api/v1/auth/users/%d/memberships/%d".formatted(userId, accountId))
                .header(
                    "Authorization",
                    internalServiceAuthorization(
                        "auth-bootstrap", InternalServiceScope.AUTH_BOOTSTRAP))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "membershipRole": "%s",
                      "membershipStatus": "%s"
                    }
                    """
                        .formatted(membershipRole, membershipStatus)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(userId))
        .andExpect(jsonPath("$.accountId").value(accountId))
        .andExpect(jsonPath("$.membershipRole").value(membershipRole))
        .andExpect(jsonPath("$.membershipStatus").value(membershipStatus));
  }

  private void upsertVerifiedContact(
      long userId, String contactChannel, String providerDestination) {
    commit(
        transactionManager,
        () ->
            jdbcTemplate.update(
                """
                INSERT INTO bank_user_verified_contact (
                    user_id,
                    contact_channel,
                    provider_destination,
                    verified_at,
                    created_at,
                    updated_at
                ) VALUES (
                    :userId,
                    :contactChannel,
                    :providerDestination,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                ON CONFLICT (user_id, contact_channel)
                DO UPDATE SET provider_destination = EXCLUDED.provider_destination,
                              verified_at = EXCLUDED.verified_at,
                              updated_at = EXCLUDED.updated_at
                """,
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                    .addValue("userId", userId)
                    .addValue("contactChannel", contactChannel)
                    .addValue("providerDestination", providerDestination)));
  }

  private void updateLegacyUserStatus(
      long userId, String userStatus, String reason, String requestId) throws Exception {
    mockMvc
        .perform(
            put("/internal/api/v1/auth/users/%d/status".formatted(userId))
                .header(
                    "Authorization",
                    internalServiceAuthorization(
                        AUTH_ADMIN_SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "userStatus": "%s",
                      "reason": "%s"
                    }
                    """
                        .formatted(userStatus, reason)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(userId))
        .andExpect(jsonPath("$.userStatus").value(userStatus));
  }

  private void updateAccountStatus(long accountId, String accountStatus, String requestId)
      throws Exception {
    mockMvc
        .perform(
            put("/internal/api/v1/accounts/%d/status".formatted(accountId))
                .header(
                    "Authorization",
                    internalServiceAuthorization(
                        "account-admin", InternalServiceScope.ACCOUNT_ADMIN))
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accountStatus": "%s"
                    }
                    """
                        .formatted(accountStatus)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(accountId))
        .andExpect(jsonPath("$.accountStatus").value(accountStatus));
  }

  private void updateMembershipStatus(
      long userId,
      long accountId,
      String membershipStatus,
      String reasonCode,
      String reasonDetail,
      String requestId)
      throws Exception {
    mockMvc
        .perform(
            put("/internal/api/v1/auth/users/%d/memberships/%d/status".formatted(userId, accountId))
                .header(
                    "Authorization",
                    internalServiceAuthorization(
                        AUTH_ADMIN_SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "membershipStatus": "%s",
                      "reasonCode": "%s",
                      "reasonDetail": "%s"
                    }
                    """
                        .formatted(membershipStatus, reasonCode, reasonDetail)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(userId))
        .andExpect(jsonPath("$.accountId").value(accountId))
        .andExpect(jsonPath("$.membershipStatus").value(membershipStatus));
  }

  private void updateLegacyMembershipStatus(
      long userId, long accountId, String membershipStatus, String reason, String requestId)
      throws Exception {
    mockMvc
        .perform(
            put("/internal/api/v1/auth/users/%d/memberships/%d/status".formatted(userId, accountId))
                .header(
                    "Authorization",
                    internalServiceAuthorization(
                        AUTH_ADMIN_SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .header("X-Request-Id", requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "membershipStatus": "%s",
                      "reason": "%s"
                    }
                    """
                        .formatted(membershipStatus, reason)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(userId))
        .andExpect(jsonPath("$.accountId").value(accountId))
        .andExpect(jsonPath("$.membershipStatus").value(membershipStatus));
  }

  private java.util.Optional<AuthStatusChangeAuditView> loadAuditByRequestId(String requestId) {
    return jdbcTemplate
        .query(
            """
            SELECT request_id,
                   actor_subject,
                   change_type,
                   target_user_id,
                   target_account_id,
                   before_status,
                   after_status,
                   reason_code,
                   reason,
                   outcome,
                   created_at
            FROM auth_status_change_audit
            WHERE request_id = :requestId
            ORDER BY id DESC
            LIMIT 1
            """,
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("requestId", requestId),
            (rs, rowNum) -> {
              Long targetAccountId = rs.getObject("target_account_id", Long.class);
              return new AuthStatusChangeAuditView(
                  rs.getString("request_id"),
                  rs.getString("actor_subject"),
                  rs.getString("change_type"),
                  rs.getLong("target_user_id"),
                  targetAccountId,
                  rs.getString("before_status"),
                  rs.getString("after_status"),
                  rs.getString("reason_code"),
                  rs.getString("reason"),
                  rs.getString("outcome"),
                  rs.getTimestamp("created_at").toInstant());
            })
        .stream()
        .findFirst();
  }

  private String internalServiceAuthorization(String subject, InternalServiceScope scope) {
    return "Bearer " + internalServiceTokenIssuer.issue(subject, java.util.Set.of(scope));
  }

  private String issueLegacyAccessToken(String subject, long userId) throws Exception {
    Instant issuedAt = Instant.now();
    JWTClaimsSet claimsSet =
        new JWTClaimsSet.Builder()
            .subject(subject)
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(issuedAt.plusSeconds(900)))
            .claim("user_id", userId)
            .build();
    SignedJWT signedJwt =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.HS256).type(JOSEObjectType.JWT).build(), claimsSet);
    signedJwt.sign(new MACSigner(TEST_SECRET.getBytes(StandardCharsets.UTF_8)));
    return signedJwt.serialize();
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

  private RefreshTokenSessionView loadRefreshTokenSession(String refreshToken) {
    return jdbcTemplate
        .query(
            """
            SELECT session_status,
                   expires_at,
                   last_used_at,
                   rotated_at,
                   replaced_by_session_id,
                   device_name,
                   ip_address
            FROM auth_refresh_token_session
            WHERE token_hash = :tokenHash
            """,
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("tokenHash", refreshTokenSecretPort.hash(refreshToken)),
            (rs, rowNum) ->
                new RefreshTokenSessionView(
                    rs.getString("session_status"),
                    toInstant(rs.getTimestamp("expires_at")),
                    toInstant(rs.getTimestamp("last_used_at")),
                    toInstant(rs.getTimestamp("rotated_at")),
                    rs.getObject("replaced_by_session_id", Long.class),
                    rs.getString("device_name"),
                    rs.getString("ip_address")))
        .stream()
        .findFirst()
        .orElseThrow(() -> new AssertionError("refresh token session is not found"));
  }

  private long loadRefreshTokenSessionId(String refreshToken) {
    return jdbcTemplate
        .query(
            """
            SELECT id
            FROM auth_refresh_token_session
            WHERE token_hash = :tokenHash
            """,
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("tokenHash", refreshTokenSecretPort.hash(refreshToken)),
            (rs, rowNum) -> rs.getLong("id"))
        .stream()
        .findFirst()
        .orElseThrow(() -> new AssertionError("refresh token session id is not found"));
  }

  private TotpCredentialView loadTotpCredential(long userId) {
    return jdbcTemplate
        .query(
            """
            SELECT credential_status,
                   secret_ciphertext,
                   secret_nonce,
                   pending_expires_at,
                   verified_at,
                   last_used_at
            FROM auth_totp_credential
            WHERE user_id = :userId
            """,
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("userId", userId),
            (rs, rowNum) ->
                new TotpCredentialView(
                    rs.getString("credential_status"),
                    rs.getString("secret_ciphertext"),
                    rs.getString("secret_nonce"),
                    toInstant(rs.getTimestamp("pending_expires_at")),
                    toInstant(rs.getTimestamp("verified_at")),
                    toInstant(rs.getTimestamp("last_used_at"))))
        .stream()
        .findFirst()
        .orElseThrow(() -> new AssertionError("totp credential is not found"));
  }

  private TotpLoginChallengeView loadTotpLoginChallenge(String challengeId) {
    return jdbcTemplate
        .query(
            """
            SELECT challenge_status,
                   attempt_count,
                   expires_at,
                   verified_at,
                   device_name,
                   ip_address
            FROM auth_totp_login_challenge
            WHERE challenge_id = :challengeId
            """,
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("challengeId", challengeId),
            (rs, rowNum) ->
                new TotpLoginChallengeView(
                    rs.getString("challenge_status"),
                    rs.getInt("attempt_count"),
                    toInstant(rs.getTimestamp("expires_at")),
                    toInstant(rs.getTimestamp("verified_at")),
                    rs.getString("device_name"),
                    rs.getString("ip_address")))
        .stream()
        .findFirst()
        .orElseThrow(() -> new AssertionError("totp login challenge is not found"));
  }

  private int countBackupCodesByStatus(long userId, String codeStatus) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM auth_mfa_backup_code
            WHERE user_id = :userId
              AND code_status = :codeStatus
            """,
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("codeStatus", codeStatus),
            Integer.class);
    return count == null ? 0 : count;
  }

  private int countBackupCodeRowsMatchingPlainValue(String plainCode) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM auth_mfa_backup_code
            WHERE code_hash = :plainCode
            """,
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("plainCode", plainCode),
            Integer.class);
    return count == null ? 0 : count;
  }

  private int countActiveRefreshSessions(long userId) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM auth_refresh_token_session
            WHERE user_id = :userId
              AND session_status = 'ACTIVE'
            """,
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("userId", userId),
            Integer.class);
    return count == null ? 0 : count;
  }

  private int countRememberDevicesByStatus(long userId, String deviceStatus) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM auth_mfa_remember_device
            WHERE user_id = :userId
              AND device_status = :deviceStatus
            """,
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("deviceStatus", deviceStatus),
            Integer.class);
    return count == null ? 0 : count;
  }

  private int countActiveRememberDevicesByToken(long userId, String rememberDeviceToken) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM auth_mfa_remember_device
            WHERE user_id = :userId
              AND device_status = 'ACTIVE'
              AND token_hash = :tokenHash
            """,
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("tokenHash", rememberDeviceSecretPort.hash(rememberDeviceToken)),
            Integer.class);
    return count == null ? 0 : count;
  }

  private int countTotpCredentialRows(long userId) {
    Integer count =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*)
            FROM auth_totp_credential
            WHERE user_id = :userId
            """,
            new org.springframework.jdbc.core.namedparam.MapSqlParameterSource()
                .addValue("userId", userId),
            Integer.class);
    return count == null ? 0 : count;
  }

  private TokenPairResponseView readTokenPair(MvcResult result) throws Exception {
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    assertEquals("SUCCESS", body.get("status").asText());
    assertEquals("Bearer", body.get("tokenType").asText());
    assertNotNull(body.get("expiresAt"));
    assertNotNull(body.get("refreshExpiresAt"));
    String refreshToken = body.get("refreshToken").asText();
    String refreshDeviceBindingToken = readRefreshDeviceBindingToken(result);
    refreshDeviceBindingTokenByRefreshToken.put(refreshToken, refreshDeviceBindingToken);
    return new TokenPairResponseView(
        body.get("status").asText(),
        body.get("accessToken").asText(),
        refreshToken,
        body.get("tokenType").asText(),
        Instant.parse(body.get("expiresAt").asText()),
        Instant.parse(body.get("refreshExpiresAt").asText()),
        body.get("userId").asLong(),
        refreshDeviceBindingToken);
  }

  private String readRefreshDeviceBindingToken(MvcResult result) {
    String setCookie = findSetCookieHeader(result, REFRESH_DEVICE_COOKIE_NAME);
    assertNotNull(setCookie);
    assertTrue(setCookie.startsWith(REFRESH_DEVICE_COOKIE_NAME + "="));
    int valueStartIndex = (REFRESH_DEVICE_COOKIE_NAME + "=").length();
    int valueEndIndex = setCookie.indexOf(';');
    return valueEndIndex >= 0
        ? setCookie.substring(valueStartIndex, valueEndIndex)
        : setCookie.substring(valueStartIndex);
  }

  private String readRememberDeviceToken(MvcResult result) {
    String setCookieHeader = findSetCookieHeader(result, REMEMBER_DEVICE_COOKIE_NAME);
    assertNotNull(setCookieHeader);
    assertTrue(setCookieHeader.contains(REMEMBER_DEVICE_COOKIE_NAME + "="));
    assertTrue(setCookieHeader.contains("HttpOnly"));
    assertTrue(setCookieHeader.contains("Secure"));
    assertTrue(setCookieHeader.contains("SameSite=Lax"));
    String cookiePrefix = REMEMBER_DEVICE_COOKIE_NAME + "=";
    int valueStart = setCookieHeader.indexOf(cookiePrefix);
    assertTrue(valueStart >= 0);
    valueStart += cookiePrefix.length();
    int valueEnd = setCookieHeader.indexOf(';', valueStart);
    return setCookieHeader.substring(valueStart, valueEnd);
  }

  private void assertRememberDeviceCleared(MvcResult result) {
    String setCookieHeader = findSetCookieHeader(result, REMEMBER_DEVICE_COOKIE_NAME);
    assertNotNull(setCookieHeader);
    assertTrue(setCookieHeader.contains(REMEMBER_DEVICE_COOKIE_NAME + "="));
    assertTrue(setCookieHeader.contains("Max-Age=0"));
    assertTrue(setCookieHeader.contains("HttpOnly"));
    assertTrue(setCookieHeader.contains("Secure"));
    assertTrue(setCookieHeader.contains("SameSite=Lax"));
  }

  private String findSetCookieHeader(MvcResult result, String cookieName) {
    return result.getResponse().getHeaders("Set-Cookie").stream()
        .filter(header -> header.startsWith(cookieName + "="))
        .findFirst()
        .orElse(null);
  }

  private RememberDeviceSessionView enableRememberDevice(
      String requestPrefix, RequestClientMetadata challengeClientMetadata) throws Exception {
    TokenPairResponseView initialSession =
        loginResult("alice", "password123!", requestPrefix + "-login-001", WINDOWS_CHROME);
    TotpEnrollmentStartResponseView enrollment =
        startTotpEnrollment(initialSession.accessToken(), requestPrefix + "-start-001");
    verifyTotpEnrollment(
        initialSession.accessToken(),
        currentTotpCode(enrollment.secretKey()),
        requestPrefix + "-verify-001");

    LoginChallengeResponseView challenge =
        loginChallenge(
            "alice", "password123!", requestPrefix + "-login-002", challengeClientMetadata);
    MvcResult verifyResult =
        performVerifyTotpChallenge(
                challenge.challengeId(),
                currentTotpCode(enrollment.secretKey()),
                requestPrefix + "-challenge-verify-001",
                null,
                true)
            .andExpect(status().isOk())
            .andReturn();
    return new RememberDeviceSessionView(
        readTokenPair(verifyResult), enrollment.secretKey(), readRememberDeviceToken(verifyResult));
  }

  private LoginChallengeResponseView readLoginChallenge(MvcResult result) throws Exception {
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    assertEquals("MFA_REQUIRED", body.get("status").asText());
    assertTrue(body.get("accessToken").isNull());
    assertTrue(body.get("refreshToken").isNull());
    assertTrue(body.get("userId").isNull());
    return new LoginChallengeResponseView(
        body.get("status").asText(),
        body.get("challengeId").asText(),
        body.get("challengeType").asText(),
        Instant.parse(body.get("challengeExpiresAt").asText()));
  }

  private String currentTotpCode(String secretKey) {
    byte[] rawSecret = Base32Codec.decode(secretKey);
    long counter = Instant.now().getEpochSecond() / 30L;
    return generateTotpCode(rawSecret, counter);
  }

  private String generateTotpCode(byte[] rawSecret, long counter) {
    try {
      Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(new SecretKeySpec(rawSecret, "HmacSHA1"));
      byte[] digest = mac.doFinal(ByteBuffer.allocate(Long.BYTES).putLong(counter).array());
      int offset = digest[digest.length - 1] & 0x0f;
      int binary =
          ((digest[offset] & 0x7f) << 24)
              | ((digest[offset + 1] & 0xff) << 16)
              | ((digest[offset + 2] & 0xff) << 8)
              | (digest[offset + 3] & 0xff);
      return String.format(java.util.Locale.ROOT, "%06d", binary % 1_000_000);
    } catch (Exception ex) {
      throw new AssertionError("failed to generate TOTP code", ex);
    }
  }

  private Instant toInstant(java.sql.Timestamp timestamp) {
    return timestamp == null ? null : timestamp.toInstant();
  }

  private String transactionQueryFrom() {
    return Instant.now().minus(Duration.ofHours(1)).toString();
  }

  private String transactionQueryTo() {
    return Instant.now().plus(Duration.ofHours(1)).toString();
  }

  private record AuthStatusChangeAuditView(
      String requestId,
      String actorSubject,
      String changeType,
      long targetUserId,
      Long targetAccountId,
      String beforeStatus,
      String afterStatus,
      String reasonCode,
      String reasonDetail,
      String outcome,
      java.time.Instant createdAt) {}

  private record LoginProtectionState(
      int failedLoginCount,
      Instant lastLoginFailedAt,
      Instant loginLockedUntil,
      Instant lastLoginSucceededAt) {}

  private record TokenPairResponseView(
      String status,
      String accessToken,
      String refreshToken,
      String tokenType,
      Instant expiresAt,
      Instant refreshExpiresAt,
      long userId,
      String refreshDeviceBindingToken) {}

  private record RememberDeviceSessionView(
      TokenPairResponseView session, String secretKey, String rememberDeviceToken) {}

  private record LoginChallengeResponseView(
      String status, String challengeId, String challengeType, Instant challengeExpiresAt) {}

  private record TotpEnrollmentStartResponseView(
      String status, String secretKey, String otpauthUri, Instant expiresAt) {}

  private record TotpEnrollmentVerifyResponseView(String status, Instant verifiedAt) {}

  private record BackupCodeIssueResponseView(int codeCount, List<String> backupCodes) {}

  private record TotpCredentialView(
      String credentialStatus,
      String secretCiphertext,
      String secretNonce,
      Instant pendingExpiresAt,
      Instant verifiedAt,
      Instant lastUsedAt) {}

  private record TotpLoginChallengeView(
      String challengeStatus,
      int attemptCount,
      Instant expiresAt,
      Instant verifiedAt,
      String deviceName,
      String ipAddress) {}

  private record RefreshTokenSessionView(
      String sessionStatus,
      Instant expiresAt,
      Instant lastUsedAt,
      Instant rotatedAt,
      Long replacedBySessionId,
      String deviceName,
      String ipAddress) {}

  private record RequestClientMetadata(
      String userAgent, String forwardedFor, String expectedDeviceName, String expectedIpAddress) {}
}
