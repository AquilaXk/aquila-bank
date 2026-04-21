package com.aquilabank.global.web.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.global.persistence.ledger.JdbcLedgerSnapshotReconciliationRepository;
import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenIssuer;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.context.WebApplicationContext;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {
      "spring.flyway.enabled=true",
      "management.health.db.enabled=true",
      "ledger.snapshot-reconciliation.enabled=true",
      "ledger.snapshot-reconciliation.batch-size=10",
      "ledger.snapshot-reconciliation.drift-list-limit=1",
      "ledger.snapshot-reconciliation.recovery-reason-max-length=200"
    })
class LedgerSnapshotReconciliationApiIntegrationTest extends PostgresContainerTestSupport {

  private static final Instant BASE = Instant.parse("2026-04-21T00:00:00Z");

  @Autowired private WebApplicationContext context;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private InternalServiceTokenIssuer internalServiceTokenIssuer;

  @Autowired private JdbcLedgerSnapshotReconciliationRepository repository;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    resetBankingTables(jdbcTemplate);
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void listsOpenDriftsWithConfiguredLimit() throws Exception {
    long firstAccountId = insertDriftedAccount("api drift one", 100L, 1_000L);
    long secondAccountId = insertDriftedAccount("api drift two", 200L, 2_000L);
    repository.reconcileBatch(0L, 10, BASE);

    mockMvc
        .perform(
            get("/internal/api/v1/ledger/snapshot-reconciliation/drifts")
                .header("Authorization", ledgerOpsAuthorization())
                .param("limit", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.limit").value(1))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].accountId").value(firstAccountId))
        .andExpect(jsonPath("$.items[0].snapshotAvailableBalanceMinor").value(100))
        .andExpect(jsonPath("$.items[0].expectedAvailableBalanceMinor").value(1000))
        .andExpect(jsonPath("$.items[0].driftStatus").value("OPEN"));

    mockMvc
        .perform(
            get("/internal/api/v1/ledger/snapshot-reconciliation/drifts")
                .header("Authorization", ledgerOpsAuthorization())
                .param("afterAccountId", String.valueOf(firstAccountId))
                .param("limit", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].accountId").value(secondAccountId));
  }

  @Test
  void recoversSnapshotWithInternalServiceTokenAndWritesAudit() throws Exception {
    long accountId = insertDriftedAccount("api recovery", 100L, 1_000L);
    repository.reconcileBatch(0L, 10, BASE);

    mockMvc
        .perform(
            post("/internal/api/v1/ledger/snapshot-reconciliation/accounts/%d/recovery"
                    .formatted(accountId))
                .header("Authorization", ledgerOpsAuthorization())
                .header("X-Request-Id", "snapshot-recovery-api-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "reason": "manual reconciliation recovery"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(accountId))
        .andExpect(jsonPath("$.beforeSnapshot.availableBalanceMinor").value(100))
        .andExpect(jsonPath("$.recoveredSnapshot.availableBalanceMinor").value(1000))
        .andExpect(jsonPath("$.recoveredBy").value("ledger-ops"))
        .andExpect(jsonPath("$.requestId").value("snapshot-recovery-api-001"));

    Map<String, Object> snapshot = snapshotRow(accountId);
    Map<String, Object> audit = recoveryAuditRow(accountId);
    assertThat(((Number) snapshot.get("available_balance_minor")).longValue()).isEqualTo(1_000L);
    assertThat(audit.get("recovered_by")).isEqualTo("ledger-ops");
    assertThat(audit.get("request_id")).isEqualTo("snapshot-recovery-api-001");
    assertThat(audit.get("recovery_reason")).isEqualTo("manual reconciliation recovery");
  }

  @Test
  void rejectsRecoveryWithoutOpenDrift() throws Exception {
    long accountId = insertMatchedAccount("api clean", 1_000L);
    repository.reconcileBatch(0L, 10, BASE);

    mockMvc
        .perform(
            post("/internal/api/v1/ledger/snapshot-reconciliation/accounts/%d/recovery"
                    .formatted(accountId))
                .header("Authorization", ledgerOpsAuthorization())
                .header("X-Request-Id", "snapshot-recovery-api-002")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "reason": "no drift recovery"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("open ledger snapshot drift is not found"));
  }

  @Test
  void rejectsMissingInternalServiceToken() throws Exception {
    mockMvc
        .perform(get("/internal/api/v1/ledger/snapshot-reconciliation/drifts"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("internal service token is invalid"));
  }

  private long insertDriftedAccount(String displayName, long snapshotBalance, long ledgerBalance) {
    return insertAccount(displayName, snapshotBalance, ledgerBalance, true);
  }

  private long insertMatchedAccount(String displayName, long ledgerBalance) {
    return insertAccount(displayName, ledgerBalance, ledgerBalance, false);
  }

  private long insertAccount(
      String displayName, long snapshotBalance, long ledgerBalance, boolean forceWrongLastEntryId) {
    long[] accountId = new long[1];
    commit(
        transactionManager,
        () -> {
          accountId[0] = insertBankAccount(displayName);
          long ledgerEntryId = insertLedgerEntry(accountId[0], ledgerBalance);
          insertSnapshot(accountId[0], snapshotBalance, forceWrongLastEntryId ? 0L : ledgerEntryId);
        });
    return accountId[0];
  }

  private long insertBankAccount(String displayName) {
    Long id =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO bank_account (
                account_number,
                display_name,
                account_status,
                currency_code,
                created_at,
                updated_at
            )
            VALUES (
                '100' || LPAD(nextval('bank_account_number_seq')::text, 11, '0'),
                :displayName,
                'ACTIVE',
                'KRW',
                :now,
                :now
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("displayName", displayName)
                .addValue("now", Timestamp.from(BASE)),
            Long.class);
    if (id == null) {
      throw new IllegalStateException("bank_account insert did not return id");
    }
    return id;
  }

  private long insertLedgerEntry(long accountId, long amountMinor) {
    Long id =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO ledger_entry (
                account_id,
                transaction_reference,
                entry_reference,
                direction,
                entry_status,
                amount_minor,
                currency_code,
                booked_at,
                metadata,
                created_at,
                updated_at
            )
            VALUES (
                :accountId,
                'TRX-' || gen_random_uuid(),
                'ENT-' || gen_random_uuid(),
                'CREDIT',
                'BOOKED',
                :amountMinor,
                'KRW',
                :bookedAt,
                '{}'::jsonb,
                :bookedAt,
                :bookedAt
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("accountId", accountId)
                .addValue("amountMinor", amountMinor)
                .addValue("bookedAt", Timestamp.from(BASE)),
            Long.class);
    if (id == null) {
      throw new IllegalStateException("ledger_entry insert did not return id");
    }
    return id;
  }

  private void insertSnapshot(long accountId, long availableBalanceMinor, long lastLedgerEntryId) {
    jdbcTemplate.update(
        """
        INSERT INTO account_balance_snapshot (
            account_id,
            last_applied_ledger_entry_id,
            available_balance_minor,
            pending_balance_minor,
            currency_code,
            updated_at
        )
        VALUES (
            :accountId,
            :lastLedgerEntryId,
            :availableBalanceMinor,
            0,
            'KRW',
            :updatedAt
        )
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("lastLedgerEntryId", lastLedgerEntryId)
            .addValue("availableBalanceMinor", availableBalanceMinor)
            .addValue("updatedAt", Timestamp.from(BASE)));
  }

  private Map<String, Object> snapshotRow(long accountId) {
    return jdbcTemplate.queryForMap(
        """
        SELECT available_balance_minor
        FROM account_balance_snapshot
        WHERE account_id = :accountId
        """,
        new MapSqlParameterSource().addValue("accountId", accountId));
  }

  private Map<String, Object> recoveryAuditRow(long accountId) {
    return jdbcTemplate.queryForMap(
        """
        SELECT recovered_by,
               request_id,
               recovery_reason
        FROM ledger_snapshot_recovery_audit
        WHERE account_id = :accountId
        """,
        new MapSqlParameterSource().addValue("accountId", accountId));
  }

  private String ledgerOpsAuthorization() {
    return "Bearer "
        + internalServiceTokenIssuer.issue(
            "ledger-ops", java.util.Set.of(InternalServiceScope.LEDGER_OPS));
  }
}
