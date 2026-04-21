package com.aquilabank.global.web.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenIssuer;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
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
      "ledger.command-idempotency.ops.list-limit=2",
      "ledger.command-idempotency.ops.recovery-batch-size=1",
      "ledger.command-idempotency.cleanup.retention-days=14"
    })
class CommandIdempotencyOpsApiIntegrationTest extends PostgresContainerTestSupport {

  private static final Instant BASE = Instant.parse("2026-04-21T00:00:00Z");

  @Autowired private WebApplicationContext context;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private InternalServiceTokenIssuer internalServiceTokenIssuer;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    resetBankingTables(jdbcTemplate);
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void returnsSummaryAndStaleStartedRowsWithInternalServiceToken() throws Exception {
    commit(
        transactionManager,
        () -> {
          insertIdempotency("ops-started-stale-001", "STARTED", BASE.minusSeconds(60));
          insertIdempotency("ops-started-stale-002", "STARTED", BASE.minusSeconds(50));
          insertIdempotency("ops-started-fresh-001", "STARTED", Instant.now().plusSeconds(60));
          insertIdempotency("ops-completed-old-001", "COMPLETED", BASE.minusSeconds(120));
          insertIdempotency("ops-failed-old-001", "FAILED", BASE.minusSeconds(120));
        });

    mockMvc
        .perform(
            get("/internal/api/v1/ledger/command-idempotency/summary")
                .header("Authorization", ledgerOpsAuthorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.startedCount").value(3))
        .andExpect(jsonPath("$.staleStartedCount", greaterThanOrEqualTo(2)))
        .andExpect(jsonPath("$.completedCount").value(1))
        .andExpect(jsonPath("$.failedCount").value(1));

    mockMvc
        .perform(
            get("/internal/api/v1/ledger/command-idempotency/stale-started")
                .header("Authorization", ledgerOpsAuthorization())
                .param("limit", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.limit").value(2))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].idempotencyKey").value("ops-started-stale-001"))
        .andExpect(jsonPath("$.items[1].idempotencyKey").value("ops-started-stale-002"));
  }

  @Test
  void recoversStaleStartedRowsWithConfiguredBatchLimit() throws Exception {
    commit(
        transactionManager,
        () -> {
          insertIdempotency("ops-recover-stale-001", "STARTED", BASE.minusSeconds(60));
          insertIdempotency("ops-recover-stale-002", "STARTED", BASE.minusSeconds(50));
          insertIdempotency("ops-recover-fresh-001", "STARTED", Instant.now().plusSeconds(60));
        });

    mockMvc
        .perform(
            post("/internal/api/v1/ledger/command-idempotency/recovery/stale-started")
                .header("Authorization", ledgerOpsAuthorization()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.staleAfterSeconds").value(30))
        .andExpect(jsonPath("$.recoveredCount").value(1));

    assertThat(statusOf("ops-recover-stale-001")).isEqualTo("FAILED");
    assertThat(statusOf("ops-recover-stale-002")).isEqualTo("STARTED");
    assertThat(statusOf("ops-recover-fresh-001")).isEqualTo("STARTED");
  }

  @Test
  void rejectsMissingInternalServiceToken() throws Exception {
    mockMvc
        .perform(get("/internal/api/v1/ledger/command-idempotency/summary"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("internal service token is invalid"));
  }

  private void insertIdempotency(String key, String status, Instant lockedUntil) {
    Instant updatedAt =
        key.contains("fresh") ? Instant.now().minusSeconds(5) : lockedUntil.minusSeconds(10);
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
            .addValue("fingerprint", "fp-" + key)
            .addValue("status", status)
            .addValue("responseCode", "COMPLETED".equals(status) ? 200 : null)
            .addValue("responsePayload", "COMPLETED".equals(status) ? "{\"ok\":true}" : null)
            .addValue("lockedUntil", Timestamp.from(lockedUntil))
            .addValue("createdAt", Timestamp.from(updatedAt.minusSeconds(30)))
            .addValue("updatedAt", Timestamp.from(updatedAt)));
  }

  private String statusOf(String key) {
    return jdbcTemplate.queryForObject(
        """
        SELECT processing_status
        FROM command_idempotency
        WHERE idempotency_key = :key
        """,
        new MapSqlParameterSource().addValue("key", key),
        String.class);
  }

  private String ledgerOpsAuthorization() {
    return "Bearer "
        + internalServiceTokenIssuer.issue(
            "ledger-ops", java.util.Set.of(InternalServiceScope.LEDGER_OPS));
  }
}
