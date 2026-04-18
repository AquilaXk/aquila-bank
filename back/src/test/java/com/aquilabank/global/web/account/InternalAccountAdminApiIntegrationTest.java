package com.aquilabank.global.web.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.account.model.AccountBootstrapCommand;
import com.aquilabank.domain.account.model.AccountBootstrapResult;
import com.aquilabank.domain.account.usecase.AccountBootstrapUseCase;
import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenIssuer;
import com.aquilabank.support.PostgresContainerTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class InternalAccountAdminApiIntegrationTest extends PostgresContainerTestSupport {
  private static final String REQUEST_ID_HEADER = "X-Request-Id";

  @Autowired private WebApplicationContext context;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private AccountBootstrapUseCase accountBootstrapUseCase;

  @Autowired private InternalServiceTokenIssuer internalServiceTokenIssuer;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void updatesAccountStatusAndPersistsUpdatedRow() throws Exception {
    AccountBootstrapResult account =
        accountBootstrapUseCase.bootstrap(
            new AccountBootstrapCommand("main account", "KRW", 10000L));
    String requestId = "account-closed-request";

    mockMvc
        .perform(
            put("/internal/api/v1/accounts/%d/status".formatted(account.accountId()))
                .header(
                    "Authorization",
                    internalServiceAuthorization(
                        "account-admin", InternalServiceScope.ACCOUNT_ADMIN))
                .header(REQUEST_ID_HEADER, requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accountStatus": "CLOSED"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(account.accountId()))
        .andExpect(jsonPath("$.accountStatus").value("CLOSED"))
        .andExpect(jsonPath("$.availableBalanceMinor").value(10000L));

    assertEquals("CLOSED", loadAccountStatus(account.accountId()));
    AccountStatusChangeAuditView audit =
        loadAuditByRequestId(requestId)
            .orElseThrow(() -> new AssertionError("account status audit is not found"));
    assertEquals(requestId, audit.requestId());
    assertEquals("account-admin", audit.actorSubject());
    assertEquals(account.accountId(), audit.targetAccountId());
    assertEquals("ACTIVE", audit.beforeStatus());
    assertEquals("CLOSED", audit.afterStatus());
    assertNotNull(audit.createdAt());
  }

  @Test
  void returnsNotFoundWhenAccountDoesNotExist() throws Exception {
    mockMvc
        .perform(
            put("/internal/api/v1/accounts/999/status")
                .header(
                    "Authorization",
                    internalServiceAuthorization(
                        "account-admin", InternalServiceScope.ACCOUNT_ADMIN))
                .header(REQUEST_ID_HEADER, "missing-account-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accountStatus": "LOCKED"
                    }
                    """))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("account summary is not found"));
  }

  @Test
  void getsStatusChangeAuditByRequestId() throws Exception {
    AccountBootstrapResult account =
        accountBootstrapUseCase.bootstrap(
            new AccountBootstrapCommand("main account", "KRW", 10000L));
    String requestId = "account-lock-request";

    mockMvc
        .perform(
            put("/internal/api/v1/accounts/%d/status".formatted(account.accountId()))
                .header(
                    "Authorization",
                    internalServiceAuthorization(
                        "account-admin", InternalServiceScope.ACCOUNT_ADMIN))
                .header(REQUEST_ID_HEADER, requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accountStatus": "LOCKED"
                    }
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/internal/api/v1/accounts/status-change-audits/by-request-id")
                .header(
                    "Authorization",
                    internalServiceAuthorization(
                        "account-admin", InternalServiceScope.ACCOUNT_ADMIN))
                .param("requestId", requestId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestId").value(requestId))
        .andExpect(jsonPath("$.actorSubject").value("account-admin"))
        .andExpect(jsonPath("$.targetAccountId").value(account.accountId()))
        .andExpect(jsonPath("$.beforeStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.afterStatus").value("LOCKED"))
        .andExpect(jsonPath("$.createdAt").exists());
  }

  private String internalServiceAuthorization(String subject, InternalServiceScope scope) {
    return "Bearer " + internalServiceTokenIssuer.issue(subject, java.util.Set.of(scope));
  }

  private java.util.Optional<AccountStatusChangeAuditView> loadAuditByRequestId(String requestId) {
    return jdbcTemplate
        .query(
            """
            SELECT request_id,
                   actor_subject,
                   target_account_id,
                   before_status,
                   after_status,
                   created_at
            FROM account_status_change_audit
            WHERE request_id = :requestId
            ORDER BY id DESC
            LIMIT 1
            """,
            new MapSqlParameterSource().addValue("requestId", requestId),
            (rs, rowNum) ->
                new AccountStatusChangeAuditView(
                    rs.getString("request_id"),
                    rs.getString("actor_subject"),
                    rs.getLong("target_account_id"),
                    rs.getString("before_status"),
                    rs.getString("after_status"),
                    rs.getTimestamp("created_at").toInstant()))
        .stream()
        .findFirst();
  }

  private String loadAccountStatus(long accountId) {
    return jdbcTemplate.queryForObject(
        """
        SELECT account_status
        FROM bank_account
        WHERE id = :accountId
        """,
        new MapSqlParameterSource().addValue("accountId", accountId),
        String.class);
  }

  private record AccountStatusChangeAuditView(
      String requestId,
      String actorSubject,
      long targetAccountId,
      String beforeStatus,
      String afterStatus,
      java.time.Instant createdAt) {}
}
