package com.aquilabank.global.persistence.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabank.domain.ledger.exception.LedgerSnapshotOpenDriftNotFoundException;
import com.aquilabank.domain.ledger.model.LedgerSnapshotDriftRecord;
import com.aquilabank.domain.ledger.model.LedgerSnapshotReconciliationBatchResult;
import com.aquilabank.domain.ledger.model.LedgerSnapshotRecoveryCommand;
import com.aquilabank.domain.ledger.model.LedgerSnapshotRecoveryResult;
import com.aquilabank.support.PostgresContainerTestSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@ActiveProfiles("test")
@SpringBootTest(properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class JdbcLedgerSnapshotReconciliationRepositoryIntegrationTest
    extends PostgresContainerTestSupport {

  private static final Instant BASE = Instant.parse("2026-04-21T00:00:00Z");

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private PlatformTransactionManager transactionManager;

  @Autowired private JdbcLedgerSnapshotReconciliationRepository repository;

  @BeforeEach
  void setUp() {
    resetBankingTables(jdbcTemplate);
  }

  @Test
  void storesOpenDriftAndResolvesItWhenSnapshotMatchesLedgerAggregate() {
    long driftedAccountId =
        commitAccount(
            "drifted account",
            snapshot(6_500L, 0L, 0L),
            ledger("CREDIT", "BOOKED", 10_000L),
            ledger("DEBIT", "BOOKED", 3_000L));
    long matchedAccountId =
        commitAccount(
            "matched account", snapshot(2_000L, 0L, null), ledger("CREDIT", "BOOKED", 2_000L));

    LedgerSnapshotReconciliationBatchResult first = repository.reconcileBatch(0L, 10, BASE);
    List<LedgerSnapshotDriftRecord> openDrifts = repository.findOpenDrifts(0L, 10);

    assertThat(first.checkedCount()).isEqualTo(2);
    assertThat(first.driftedCount()).isEqualTo(1);
    assertThat(first.resolvedCount()).isZero();
    assertThat(first.nextAccountId()).isEqualTo(matchedAccountId);
    assertThat(first.hasMore()).isFalse();
    assertThat(openDrifts).hasSize(1);
    LedgerSnapshotDriftRecord item = openDrifts.getFirst();
    assertThat(item.accountId()).isEqualTo(driftedAccountId);
    assertThat(item.snapshotAvailableBalanceMinor()).isEqualTo(6_500L);
    assertThat(item.expectedAvailableBalanceMinor()).isEqualTo(7_000L);
    assertThat(item.driftStatus()).isEqualTo("OPEN");

    commit(
        transactionManager,
        () ->
            updateSnapshot(driftedAccountId, 7_000L, 0L, item.expectedLastAppliedLedgerEntryId()));

    LedgerSnapshotReconciliationBatchResult second =
        repository.reconcileBatch(0L, 10, BASE.plusSeconds(60));

    assertThat(second.checkedCount()).isEqualTo(2);
    assertThat(second.driftedCount()).isZero();
    assertThat(second.resolvedCount()).isEqualTo(1);
    assertThat(repository.findOpenDrifts(0L, 10)).isEmpty();
    assertThat(driftStatus(driftedAccountId)).isEqualTo("RESOLVED");
  }

  @Test
  void recoversSnapshotFromOpenDriftAndWritesAudit() {
    long accountId =
        commitAccount(
            "recover account",
            snapshot(100L, 0L, 0L),
            ledger("CREDIT", "BOOKED", 1_500L),
            ledger("DEBIT", "BOOKED", 500L));
    repository.reconcileBatch(0L, 10, BASE);

    LedgerSnapshotRecoveryResult result =
        repository.recoverSnapshot(
            new LedgerSnapshotRecoveryCommand(
                accountId, "manual reconciliation recovery", "ledger-ops", "recover-request-001"),
            BASE.plusSeconds(30));

    Map<String, Object> snapshot = snapshotRow(accountId);
    Map<String, Object> audit = recoveryAuditRow(accountId);
    assertThat(result.accountId()).isEqualTo(accountId);
    assertThat(result.beforeSnapshot().availableBalanceMinor()).isEqualTo(100L);
    assertThat(result.recoveredSnapshot().availableBalanceMinor()).isEqualTo(1_000L);
    assertThat(((Number) snapshot.get("available_balance_minor")).longValue()).isEqualTo(1_000L);
    assertThat(((Number) snapshot.get("last_applied_ledger_entry_id")).longValue())
        .isEqualTo(result.recoveredSnapshot().lastAppliedLedgerEntryId());
    assertThat(driftStatus(accountId)).isEqualTo("RECOVERED");
    assertThat(audit.get("recovered_by")).isEqualTo("ledger-ops");
    assertThat(audit.get("request_id")).isEqualTo("recover-request-001");
    assertThat(audit.get("recovery_reason")).isEqualTo("manual reconciliation recovery");
    assertThat(((Number) audit.get("before_available_balance_minor")).longValue()).isEqualTo(100L);
    assertThat(((Number) audit.get("after_available_balance_minor")).longValue()).isEqualTo(1_000L);
  }

  @Test
  void recoveryRequiresOpenDrift() {
    long accountId =
        commitAccount(
            "clean account", snapshot(1_000L, 0L, null), ledger("CREDIT", "BOOKED", 1_000L));
    repository.reconcileBatch(0L, 10, BASE);

    assertThatThrownBy(
            () ->
                repository.recoverSnapshot(
                    new LedgerSnapshotRecoveryCommand(
                        accountId, "no drift", "ledger-ops", "recover-request-002"),
                    BASE.plusSeconds(30)))
        .isInstanceOf(LedgerSnapshotOpenDriftNotFoundException.class)
        .hasMessage("open ledger snapshot drift is not found");
  }

  private long commitAccount(String displayName, SnapshotSeed snapshot, LedgerSeed... entries) {
    long[] accountId = new long[1];
    commit(
        transactionManager,
        () -> {
          accountId[0] = insertAccount(displayName);
          long lastLedgerEntryId = 0L;
          for (LedgerSeed entry : entries) {
            lastLedgerEntryId = insertLedgerEntry(accountId[0], entry);
          }
          insertSnapshot(accountId[0], snapshot, lastLedgerEntryId);
        });
    return accountId[0];
  }

  private long insertAccount(String displayName) {
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

  private long insertLedgerEntry(long accountId, LedgerSeed entry) {
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
                :direction,
                :entryStatus,
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
                .addValue("direction", entry.direction())
                .addValue("entryStatus", entry.entryStatus())
                .addValue("amountMinor", entry.amountMinor())
                .addValue("bookedAt", Timestamp.from(BASE)),
            Long.class);
    if (id == null) {
      throw new IllegalStateException("ledger_entry insert did not return id");
    }
    return id;
  }

  private void insertSnapshot(long accountId, SnapshotSeed snapshot, long actualLastLedgerEntryId) {
    long lastLedgerEntryId =
        snapshot.lastAppliedLedgerEntryId() == null
            ? actualLastLedgerEntryId
            : snapshot.lastAppliedLedgerEntryId();
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
            :lastAppliedLedgerEntryId,
            :availableBalanceMinor,
            :pendingBalanceMinor,
            'KRW',
            :updatedAt
        )
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("lastAppliedLedgerEntryId", lastLedgerEntryId)
            .addValue("availableBalanceMinor", snapshot.availableBalanceMinor())
            .addValue("pendingBalanceMinor", snapshot.pendingBalanceMinor())
            .addValue("updatedAt", Timestamp.from(BASE)));
  }

  private void updateSnapshot(
      long accountId,
      long availableBalanceMinor,
      long pendingBalanceMinor,
      long lastAppliedLedgerEntryId) {
    jdbcTemplate.update(
        """
        UPDATE account_balance_snapshot
        SET available_balance_minor = :availableBalanceMinor,
            pending_balance_minor = :pendingBalanceMinor,
            last_applied_ledger_entry_id = :lastAppliedLedgerEntryId,
            updated_at = :updatedAt
        WHERE account_id = :accountId
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("availableBalanceMinor", availableBalanceMinor)
            .addValue("pendingBalanceMinor", pendingBalanceMinor)
            .addValue("lastAppliedLedgerEntryId", lastAppliedLedgerEntryId)
            .addValue("updatedAt", Timestamp.from(BASE.plusSeconds(30))));
  }

  private Map<String, Object> snapshotRow(long accountId) {
    return jdbcTemplate.queryForMap(
        """
        SELECT available_balance_minor,
               pending_balance_minor,
               last_applied_ledger_entry_id
        FROM account_balance_snapshot
        WHERE account_id = :accountId
        """,
        new MapSqlParameterSource().addValue("accountId", accountId));
  }

  private String driftStatus(long accountId) {
    return jdbcTemplate.queryForObject(
        """
        SELECT drift_status
        FROM ledger_snapshot_reconciliation_drift
        WHERE account_id = :accountId
        ORDER BY id DESC
        LIMIT 1
        """,
        new MapSqlParameterSource().addValue("accountId", accountId),
        String.class);
  }

  private Map<String, Object> recoveryAuditRow(long accountId) {
    return jdbcTemplate.queryForMap(
        """
        SELECT recovered_by,
               request_id,
               recovery_reason,
               before_available_balance_minor,
               after_available_balance_minor
        FROM ledger_snapshot_recovery_audit
        WHERE account_id = :accountId
        """,
        new MapSqlParameterSource().addValue("accountId", accountId));
  }

  private SnapshotSeed snapshot(
      long availableBalanceMinor, long pendingBalanceMinor, Long lastAppliedLedgerEntryId) {
    return new SnapshotSeed(availableBalanceMinor, pendingBalanceMinor, lastAppliedLedgerEntryId);
  }

  private LedgerSeed ledger(String direction, String entryStatus, long amountMinor) {
    return new LedgerSeed(direction, entryStatus, amountMinor);
  }

  private record SnapshotSeed(
      long availableBalanceMinor, long pendingBalanceMinor, Long lastAppliedLedgerEntryId) {}

  private record LedgerSeed(String direction, String entryStatus, long amountMinor) {}
}
