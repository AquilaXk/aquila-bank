package com.aquilabank.global.web.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.support.PostgresContainerTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class LoginAndAccountAccessApiIntegrationTest extends PostgresContainerTestSupport {

  private static final String ACCOUNT_BOOTSTRAP_TOKEN = "test-bootstrap-api-token";
  private static final String AUTH_BOOTSTRAP_TOKEN = "test-auth-bootstrap-api-token";
  private static final String AUTH_ADMIN_SUBJECT = "ops-admin";

  @Autowired private WebApplicationContext context;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private ObjectMapper objectMapper;

  private MockMvc mockMvc;
  private long userId;
  private long allowedSourceAccountId;
  private long targetAccountId;
  private long deniedAccountId;

  @BeforeEach
  void setUpDatabase() throws Exception {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    resetBankingTables(jdbcTemplate);

    allowedSourceAccountId = bootstrapAccount("allowed source", 10_000L);
    targetAccountId = bootstrapAccount("allowed target", 0L);
    deniedAccountId = bootstrapAccount("denied source", 5_000L);

    userId = bootstrapUser("alice", "Alice", "password123!");
    upsertMembership(userId, allowedSourceAccountId, "OWNER", "ACTIVE");
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
                .param("from", "2026-04-01T00:00:00Z")
                .param("to", "2026-04-17T00:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].accountId").value(allowedSourceAccountId))
        .andExpect(jsonPath("$.items[0].transactionReference").isString());
  }

  @Test
  void rejectsUnmappedAccountAccess() throws Exception {
    String token = login("alice", "password123!");

    mockMvc
        .perform(
            get("/api/v1/transactions")
                .header("Authorization", "Bearer " + token)
                .param("accountId", String.valueOf(deniedAccountId))
                .param("from", "2026-04-01T00:00:00Z")
                .param("to", "2026-04-17T00:00:00Z"))
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
  void membershipUpsertUpdatesTransferPermission() throws Exception {
    upsertMembership(userId, allowedSourceAccountId, "VIEWER", "ACTIVE");
    String token = login("alice", "password123!");

    mockMvc
        .perform(
            get("/api/v1/transactions")
                .header("Authorization", "Bearer " + token)
                .param("accountId", String.valueOf(allowedSourceAccountId))
                .param("from", "2026-04-01T00:00:00Z")
                .param("to", "2026-04-17T00:00:00Z"))
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
  void internalAuthAdminLookupReturnsBootstrappedUserAndMembership() throws Exception {
    mockMvc
        .perform(
            get("/internal/api/v1/auth/users/%d".formatted(userId))
                .header("X-Auth-Bootstrap-Token", AUTH_BOOTSTRAP_TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(userId))
        .andExpect(jsonPath("$.loginId").value("alice"))
        .andExpect(jsonPath("$.userStatus").value("ACTIVE"));

    mockMvc
        .perform(
            get("/internal/api/v1/auth/users/by-login-id")
                .header("X-Auth-Bootstrap-Token", AUTH_BOOTSTRAP_TOKEN)
                .param("loginId", "alice"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Alice"));

    mockMvc
        .perform(
            get("/internal/api/v1/auth/users/%d/memberships/%d"
                    .formatted(userId, allowedSourceAccountId))
                .header("X-Auth-Bootstrap-Token", AUTH_BOOTSTRAP_TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(userId))
        .andExpect(jsonPath("$.accountId").value(allowedSourceAccountId))
        .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"));
  }

  @Test
  void disabledUserCannotLoginOrUseExistingJwt() throws Exception {
    String token = login("alice", "password123!");
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

    mockMvc
        .perform(
            get("/api/v1/transactions")
                .header("Authorization", "Bearer " + token)
                .param("accountId", String.valueOf(allowedSourceAccountId))
                .param("from", "2026-04-01T00:00:00Z")
                .param("to", "2026-04-17T00:00:00Z"))
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
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));
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
                .param("from", "2026-04-01T00:00:00Z")
                .param("to", "2026-04-17T00:00:00Z"))
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
                .header("X-Auth-Bootstrap-Token", AUTH_BOOTSTRAP_TOKEN)
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

  private String login(String loginId, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "loginId": "%s",
                          "password": "%s"
                        }
                        """
                            .formatted(loginId, password)))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    assertEquals("Bearer", body.get("tokenType").asText());
    assertNotNull(body.get("expiresAt"));
    return body.get("accessToken").asText();
  }

  private long bootstrapAccount(String displayName, long initialBalanceMinor) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/internal/api/v1/accounts/bootstrap")
                    .header("X-Bootstrap-Token", ACCOUNT_BOOTSTRAP_TOKEN)
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
                    .header("X-Auth-Bootstrap-Token", AUTH_BOOTSTRAP_TOKEN)
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
                .header("X-Auth-Bootstrap-Token", AUTH_BOOTSTRAP_TOKEN)
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

  private void updateLegacyUserStatus(
      long userId, String userStatus, String reason, String requestId)
      throws Exception {
    mockMvc
        .perform(
            put("/internal/api/v1/auth/users/%d/status".formatted(userId))
                .header("X-Auth-Bootstrap-Token", AUTH_BOOTSTRAP_TOKEN)
                .header("X-Subject", AUTH_ADMIN_SUBJECT)
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
                .header("X-Auth-Bootstrap-Token", AUTH_BOOTSTRAP_TOKEN)
                .header("X-Subject", AUTH_ADMIN_SUBJECT)
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
}
