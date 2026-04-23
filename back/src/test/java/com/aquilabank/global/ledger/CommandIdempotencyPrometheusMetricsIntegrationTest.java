package com.aquilabank.global.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenIssuer;
import com.aquilabank.support.PostgresContainerTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.flyway.enabled=true",
      "management.health.db.enabled=true",
      "ledger.command-idempotency.ops.enabled=true",
      "ledger.command-idempotency.ops.stale-after-seconds=30",
      "ledger.command-idempotency.ops.recovery-batch-size=1",
      "ledger.command-idempotency.cleanup.enabled=true",
      "ledger.command-idempotency.cleanup.retention-days=14"
    })
class CommandIdempotencyPrometheusMetricsIntegrationTest extends PostgresContainerTestSupport {

  @Autowired private WebApplicationContext context;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private InternalServiceTokenIssuer internalServiceTokenIssuer;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private CommandIdempotencyCleanupPoller commandIdempotencyCleanupPoller;

  private MockMvc mockMvc;
  private long sourceAccountId;
  private long targetAccountId;

  @BeforeEach
  void setUp() throws Exception {
    resetBankingTables(jdbcTemplate);
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    sourceAccountId = bootstrapAccount("metrics source", 10_000L);
    targetAccountId = bootstrapAccount("metrics target", 0L);
  }

  @Test
  void exportsCommandIdempotencySummaryConflictRecoveryAndCleanupMetrics() throws Exception {
    Instant now = Instant.now();
    commit(
        transactionManager,
        () -> {
          insertIdempotency("metrics-stale-001", "fp-stale-001", "STARTED", now.minusSeconds(90));
          insertIdempotency("metrics-stale-002", "fp-stale-002", "STARTED", now.minusSeconds(80));
          insertIdempotency("metrics-fresh-001", "fp-fresh-001", "STARTED", now.plusSeconds(90));
          insertIdempotency(
              "metrics-completed-old-001",
              "fp-completed-001",
              "COMPLETED",
              now.minusSeconds(20L * 24 * 60 * 60));
          insertIdempotency(
              "metrics-failed-old-001",
              "fp-failed-001",
              "FAILED",
              now.minusSeconds(20L * 24 * 60 * 60));
          insertIdempotency(
              "metrics-conflict-001", "seed-conflict", "STARTED", now.plusSeconds(90));
        });

    MvcResult conflict =
        mockMvc
            .perform(
                post("/api/v1/transfers")
                    .header("X-Account-Id", String.valueOf(sourceAccountId))
                    .header("X-Request-Id", "metrics-conflict-001-request")
                    .header("Idempotency-Key", "metrics-conflict-001")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "sourceAccountId": %d,
                          "targetAccountId": %d,
                          "amountMinor": 500,
                          "currencyCode": "KRW",
                          "summary": "metrics conflict"
                        }
                        """
                            .formatted(sourceAccountId, targetAccountId)))
            .andReturn();
    assertThat(conflict.getResponse().getStatus()).isEqualTo(409);

    mockMvc
        .perform(
            post("/internal/api/v1/ledger/command-idempotency/recovery/stale-started")
                .header("Authorization", ledgerOpsAuthorization()))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isEqualTo(200));

    commandIdempotencyCleanupPoller.cleanupExpiredRecords();

    String body =
        mockMvc.perform(get("/actuator/prometheus")).andReturn().getResponse().getContentAsString();

    assertThat(body).containsPattern("aquila_command_idempotency_started_count\\s+3\\.0");
    assertThat(body).containsPattern("aquila_command_idempotency_stale_started_count\\s+1\\.0");
    assertThat(body).containsPattern("aquila_command_idempotency_failed_count\\s+1\\.0");
    assertThat(body).containsPattern("aquila_command_idempotency_cleanup_candidate_count\\s+0\\.0");
    assertThat(body)
        .containsPattern(
            "aquila_command_idempotency_conflict_count_total\\{[^\\n]*reason_code=\"DIFFERENT_REQUEST\"[^\\n]*\\}\\s+1\\.0");
    assertThat(body)
        .containsPattern("aquila_command_idempotency_recovery_recovered_count_total\\s+1\\.0");
    assertThat(body)
        .containsPattern("aquila_command_idempotency_cleanup_deleted_count_total\\s+2\\.0");
  }

  private long bootstrapAccount(String displayName, long initialBalanceMinor) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/internal/api/v1/accounts/bootstrap")
                    .header("X-Request-Id", "bootstrap-" + displayName.replace(" ", "-"))
                    .header(
                        "Authorization",
                        "Bearer "
                            + internalServiceTokenIssuer.issue(
                                "account-bootstrap",
                                java.util.Set.of(InternalServiceScope.ACCOUNT_BOOTSTRAP)))
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
            .andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    return body.get("accountId").asLong();
  }

  private void insertIdempotency(
      String key, String fingerprint, String status, Instant lockedUntil) {
    Instant updatedAt =
        switch (status) {
          case "COMPLETED", "FAILED" -> lockedUntil;
          default -> lockedUntil.minusSeconds(10);
        };
    jdbcTemplate.update(
        """
        INSERT INTO command_idempotency (
            idempotency_key,
            request_fingerprint,
            processing_status,
            response_code,
            response_payload,
            locked_until,
            created_at,
            updated_at
        )
        VALUES (
            :key,
            :fingerprint,
            :status,
            :responseCode,
            CAST(:responsePayload AS jsonb),
            :lockedUntil,
            :createdAt,
            :updatedAt
        )
        """,
        new MapSqlParameterSource()
            .addValue("key", key)
            .addValue("fingerprint", fingerprint)
            .addValue("status", status)
            .addValue("responseCode", "COMPLETED".equals(status) ? 200 : null)
            .addValue("responsePayload", "COMPLETED".equals(status) ? "{\"ok\":true}" : null)
            .addValue("lockedUntil", Timestamp.from(lockedUntil))
            .addValue("createdAt", Timestamp.from(updatedAt.minusSeconds(30)))
            .addValue("updatedAt", Timestamp.from(updatedAt)));
  }

  private String ledgerOpsAuthorization() {
    return "Bearer "
        + internalServiceTokenIssuer.issue(
            "ledger-ops", java.util.Set.of(InternalServiceScope.LEDGER_OPS));
  }
}
