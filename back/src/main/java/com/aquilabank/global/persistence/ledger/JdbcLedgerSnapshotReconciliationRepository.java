package com.aquilabank.global.persistence.ledger;

import com.aquilabank.domain.ledger.exception.LedgerSnapshotOpenDriftNotFoundException;
import com.aquilabank.domain.ledger.model.LedgerSnapshotDriftRecord;
import com.aquilabank.domain.ledger.model.LedgerSnapshotReconciliationBatchResult;
import com.aquilabank.domain.ledger.model.LedgerSnapshotRecoveryCommand;
import com.aquilabank.domain.ledger.model.LedgerSnapshotRecoveryResult;
import com.aquilabank.domain.ledger.model.LedgerSnapshotValues;
import com.aquilabank.domain.ledger.port.LedgerSnapshotDriftReadPort;
import com.aquilabank.domain.ledger.port.LedgerSnapshotReconciliationPort;
import com.aquilabank.domain.ledger.port.LedgerSnapshotRecoveryPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** snapshot batch를 먼저 제한하고 해당 계좌들의 ledger만 합산해 reconciliation 비용을 제한합니다. */
@Repository
public class JdbcLedgerSnapshotReconciliationRepository
    implements LedgerSnapshotReconciliationPort,
        LedgerSnapshotDriftReadPort,
        LedgerSnapshotRecoveryPort {

  private static final String OPEN = "OPEN";
  private static final String RESOLVED = "RESOLVED";
  private static final String RECOVERED = "RECOVERED";
  private static final RowMapper<SnapshotComparison> COMPARISON_ROW_MAPPER =
      (rs, rowNum) -> mapSnapshotComparison(rs);
  private static final RowMapper<LedgerSnapshotDriftRecord> DRIFT_ROW_MAPPER =
      (rs, rowNum) -> mapDriftRecord(rs);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcLedgerSnapshotReconciliationRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional
  public LedgerSnapshotReconciliationBatchResult reconcileBatch(
      long afterAccountId, int batchSize, Instant observedAt) {
    if (afterAccountId < 0) {
      throw new IllegalArgumentException("afterAccountId must not be negative");
    }
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be positive");
    }
    List<SnapshotComparison> items = findSnapshotComparisons(afterAccountId, batchSize);
    int driftedCount = 0;
    int resolvedCount = 0;
    long nextAccountId = afterAccountId;
    for (SnapshotComparison item : items) {
      nextAccountId = item.accountId();
      if (item.isDrifted()) {
        upsertOpenDrift(item, observedAt);
        driftedCount++;
      } else {
        resolvedCount += resolveOpenDrift(item.accountId(), observedAt);
      }
    }
    return new LedgerSnapshotReconciliationBatchResult(
        observedAt,
        nextAccountId,
        items.size(),
        driftedCount,
        resolvedCount,
        items.size() == batchSize);
  }

  @Override
  @Transactional(readOnly = true)
  public List<LedgerSnapshotDriftRecord> findOpenDrifts(long afterAccountId, int limit) {
    if (afterAccountId < 0) {
      throw new IllegalArgumentException("afterAccountId must not be negative");
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
    return jdbcTemplate.query(
        """
        SELECT id,
               account_id,
               observed_at,
               snapshot_available_balance_minor,
               expected_available_balance_minor,
               snapshot_pending_balance_minor,
               expected_pending_balance_minor,
               snapshot_last_applied_ledger_entry_id,
               expected_last_applied_ledger_entry_id,
               currency_code,
               drift_status
        FROM ledger_snapshot_reconciliation_drift
        WHERE drift_status = 'OPEN'
          AND account_id > :afterAccountId
        ORDER BY account_id ASC, id ASC
        LIMIT :limit
        """,
        new MapSqlParameterSource()
            .addValue("afterAccountId", afterAccountId)
            .addValue("limit", limit),
        DRIFT_ROW_MAPPER);
  }

  @Override
  @Transactional
  public LedgerSnapshotRecoveryResult recoverSnapshot(
      LedgerSnapshotRecoveryCommand command, Instant recoveredAt) {
    LedgerSnapshotDriftRecord drift = loadOpenDriftForUpdate(command.accountId());
    LedgerSnapshotValues beforeSnapshot = loadSnapshotForUpdate(command.accountId());
    LedgerSnapshotValues recoveredSnapshot = calculateExpectedValues(command.accountId());

    updateSnapshot(command.accountId(), recoveredSnapshot, recoveredAt);
    markDriftRecovered(drift.id(), recoveredAt);
    insertRecoveryAudit(drift.id(), command, beforeSnapshot, recoveredSnapshot, recoveredAt);

    return new LedgerSnapshotRecoveryResult(
        drift.id(),
        command.accountId(),
        beforeSnapshot,
        recoveredSnapshot,
        command.recoveredBy(),
        command.requestId(),
        command.reason(),
        recoveredAt);
  }

  private List<SnapshotComparison> findSnapshotComparisons(long afterAccountId, int batchSize) {
    return jdbcTemplate.query(
        """
        WITH batch AS (
            SELECT account_id,
                   available_balance_minor,
                   pending_balance_minor,
                   last_applied_ledger_entry_id,
                   currency_code
            FROM account_balance_snapshot
            WHERE account_id > :afterAccountId
            ORDER BY account_id ASC
            LIMIT :batchSize
        ),
        ledger_totals AS (
            SELECT b.account_id,
                   COALESCE(SUM(
                       CASE
                         WHEN le.entry_status = 'BOOKED' AND le.direction = 'CREDIT'
                           THEN le.amount_minor
                         WHEN le.entry_status = 'BOOKED' AND le.direction = 'DEBIT'
                           THEN -le.amount_minor
                         ELSE 0
                       END
                   ), 0)::bigint AS expected_available_balance_minor,
                   COALESCE(SUM(
                       CASE
                         WHEN le.entry_status = 'PENDING' AND le.direction = 'CREDIT'
                           THEN le.amount_minor
                         WHEN le.entry_status = 'PENDING' AND le.direction = 'DEBIT'
                           THEN -le.amount_minor
                         ELSE 0
                       END
                   ), 0)::bigint AS expected_pending_balance_minor,
                   COALESCE(MAX(le.id), 0)::bigint AS expected_last_applied_ledger_entry_id
            FROM batch b
            LEFT JOIN ledger_entry le ON le.account_id = b.account_id
            GROUP BY b.account_id
        )
        SELECT b.account_id,
               b.available_balance_minor AS snapshot_available_balance_minor,
               lt.expected_available_balance_minor,
               b.pending_balance_minor AS snapshot_pending_balance_minor,
               lt.expected_pending_balance_minor,
               b.last_applied_ledger_entry_id AS snapshot_last_applied_ledger_entry_id,
               lt.expected_last_applied_ledger_entry_id,
               b.currency_code
        FROM batch b
        JOIN ledger_totals lt ON lt.account_id = b.account_id
        ORDER BY b.account_id ASC
        """,
        new MapSqlParameterSource()
            .addValue("afterAccountId", afterAccountId)
            .addValue("batchSize", batchSize),
        COMPARISON_ROW_MAPPER);
  }

  private void upsertOpenDrift(SnapshotComparison item, Instant observedAt) {
    jdbcTemplate.update(
        """
        INSERT INTO ledger_snapshot_reconciliation_drift (
            account_id,
            observed_at,
            snapshot_available_balance_minor,
            expected_available_balance_minor,
            snapshot_pending_balance_minor,
            expected_pending_balance_minor,
            snapshot_last_applied_ledger_entry_id,
            expected_last_applied_ledger_entry_id,
            currency_code,
            drift_status,
            created_at,
            updated_at
        )
        VALUES (
            :accountId,
            :observedAt,
            :snapshotAvailableBalanceMinor,
            :expectedAvailableBalanceMinor,
            :snapshotPendingBalanceMinor,
            :expectedPendingBalanceMinor,
            :snapshotLastAppliedLedgerEntryId,
            :expectedLastAppliedLedgerEntryId,
            :currencyCode,
            :driftStatus,
            :observedAt,
            :observedAt
        )
        ON CONFLICT (account_id) WHERE drift_status = 'OPEN'
        DO UPDATE SET
            observed_at = EXCLUDED.observed_at,
            snapshot_available_balance_minor = EXCLUDED.snapshot_available_balance_minor,
            expected_available_balance_minor = EXCLUDED.expected_available_balance_minor,
            snapshot_pending_balance_minor = EXCLUDED.snapshot_pending_balance_minor,
            expected_pending_balance_minor = EXCLUDED.expected_pending_balance_minor,
            snapshot_last_applied_ledger_entry_id =
                EXCLUDED.snapshot_last_applied_ledger_entry_id,
            expected_last_applied_ledger_entry_id =
                EXCLUDED.expected_last_applied_ledger_entry_id,
            currency_code = EXCLUDED.currency_code,
            updated_at = EXCLUDED.updated_at
        """,
        driftParams(item, observedAt).addValue("driftStatus", OPEN));
  }

  private int resolveOpenDrift(long accountId, Instant observedAt) {
    return jdbcTemplate.update(
        """
        UPDATE ledger_snapshot_reconciliation_drift
        SET drift_status = :resolvedStatus,
            resolved_at = :observedAt,
            updated_at = :observedAt
        WHERE account_id = :accountId
          AND drift_status = :openStatus
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("observedAt", Timestamp.from(observedAt))
            .addValue("openStatus", OPEN)
            .addValue("resolvedStatus", RESOLVED));
  }

  private LedgerSnapshotDriftRecord loadOpenDriftForUpdate(long accountId) {
    return jdbcTemplate
        .query(
            """
            SELECT id,
                   account_id,
                   observed_at,
                   snapshot_available_balance_minor,
                   expected_available_balance_minor,
                   snapshot_pending_balance_minor,
                   expected_pending_balance_minor,
                   snapshot_last_applied_ledger_entry_id,
                   expected_last_applied_ledger_entry_id,
                   currency_code,
                   drift_status
            FROM ledger_snapshot_reconciliation_drift
            WHERE account_id = :accountId
              AND drift_status = 'OPEN'
            FOR UPDATE
            """,
            new MapSqlParameterSource().addValue("accountId", accountId),
            DRIFT_ROW_MAPPER)
        .stream()
        .findFirst()
        .orElseThrow(
            () ->
                new LedgerSnapshotOpenDriftNotFoundException(
                    "open ledger snapshot drift is not found"));
  }

  private LedgerSnapshotValues loadSnapshotForUpdate(long accountId) {
    return jdbcTemplate
        .query(
            """
            SELECT available_balance_minor,
                   pending_balance_minor,
                   last_applied_ledger_entry_id,
                   currency_code
            FROM account_balance_snapshot
            WHERE account_id = :accountId
            FOR UPDATE
            """,
            new MapSqlParameterSource().addValue("accountId", accountId),
            (rs, rowNum) -> mapSnapshotValues(rs))
        .stream()
        .findFirst()
        .orElseThrow(() -> new IllegalStateException("account snapshot is missing"));
  }

  private LedgerSnapshotValues calculateExpectedValues(long accountId) {
    return jdbcTemplate.queryForObject(
        """
        SELECT COALESCE(SUM(
                   CASE
                     WHEN le.entry_status = 'BOOKED' AND le.direction = 'CREDIT'
                       THEN le.amount_minor
                     WHEN le.entry_status = 'BOOKED' AND le.direction = 'DEBIT'
                       THEN -le.amount_minor
                     ELSE 0
                   END
               ), 0)::bigint AS available_balance_minor,
               COALESCE(SUM(
                   CASE
                     WHEN le.entry_status = 'PENDING' AND le.direction = 'CREDIT'
                       THEN le.amount_minor
                     WHEN le.entry_status = 'PENDING' AND le.direction = 'DEBIT'
                       THEN -le.amount_minor
                     ELSE 0
                   END
               ), 0)::bigint AS pending_balance_minor,
               COALESCE(MAX(le.id), 0)::bigint AS last_applied_ledger_entry_id,
               COALESCE(MAX(le.currency_code), snapshot.currency_code) AS currency_code
        FROM account_balance_snapshot snapshot
        LEFT JOIN ledger_entry le ON le.account_id = snapshot.account_id
        WHERE snapshot.account_id = :accountId
        GROUP BY snapshot.account_id, snapshot.currency_code
        """,
        new MapSqlParameterSource().addValue("accountId", accountId),
        (rs, rowNum) -> mapSnapshotValues(rs));
  }

  private void updateSnapshot(
      long accountId, LedgerSnapshotValues recoveredSnapshot, Instant recoveredAt) {
    jdbcTemplate.update(
        """
        UPDATE account_balance_snapshot
        SET available_balance_minor = :availableBalanceMinor,
            pending_balance_minor = :pendingBalanceMinor,
            last_applied_ledger_entry_id = :lastAppliedLedgerEntryId,
            currency_code = :currencyCode,
            updated_at = :recoveredAt
        WHERE account_id = :accountId
        """,
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("availableBalanceMinor", recoveredSnapshot.availableBalanceMinor())
            .addValue("pendingBalanceMinor", recoveredSnapshot.pendingBalanceMinor())
            .addValue("lastAppliedLedgerEntryId", recoveredSnapshot.lastAppliedLedgerEntryId())
            .addValue("currencyCode", recoveredSnapshot.currencyCode())
            .addValue("recoveredAt", Timestamp.from(recoveredAt)));
  }

  private void markDriftRecovered(long driftId, Instant recoveredAt) {
    jdbcTemplate.update(
        """
        UPDATE ledger_snapshot_reconciliation_drift
        SET drift_status = :recoveredStatus,
            recovered_at = :recoveredAt,
            updated_at = :recoveredAt
        WHERE id = :driftId
          AND drift_status = :openStatus
        """,
        new MapSqlParameterSource()
            .addValue("driftId", driftId)
            .addValue("openStatus", OPEN)
            .addValue("recoveredStatus", RECOVERED)
            .addValue("recoveredAt", Timestamp.from(recoveredAt)));
  }

  private void insertRecoveryAudit(
      long driftId,
      LedgerSnapshotRecoveryCommand command,
      LedgerSnapshotValues beforeSnapshot,
      LedgerSnapshotValues recoveredSnapshot,
      Instant recoveredAt) {
    jdbcTemplate.update(
        """
        INSERT INTO ledger_snapshot_recovery_audit (
            drift_id,
            account_id,
            before_available_balance_minor,
            after_available_balance_minor,
            before_pending_balance_minor,
            after_pending_balance_minor,
            before_last_applied_ledger_entry_id,
            after_last_applied_ledger_entry_id,
            currency_code,
            recovered_by,
            request_id,
            recovery_reason,
            recovered_at,
            created_at
        )
        VALUES (
            :driftId,
            :accountId,
            :beforeAvailableBalanceMinor,
            :afterAvailableBalanceMinor,
            :beforePendingBalanceMinor,
            :afterPendingBalanceMinor,
            :beforeLastAppliedLedgerEntryId,
            :afterLastAppliedLedgerEntryId,
            :currencyCode,
            :recoveredBy,
            :requestId,
            :recoveryReason,
            :recoveredAt,
            :recoveredAt
        )
        """,
        new MapSqlParameterSource()
            .addValue("driftId", driftId)
            .addValue("accountId", command.accountId())
            .addValue("beforeAvailableBalanceMinor", beforeSnapshot.availableBalanceMinor())
            .addValue("afterAvailableBalanceMinor", recoveredSnapshot.availableBalanceMinor())
            .addValue("beforePendingBalanceMinor", beforeSnapshot.pendingBalanceMinor())
            .addValue("afterPendingBalanceMinor", recoveredSnapshot.pendingBalanceMinor())
            .addValue("beforeLastAppliedLedgerEntryId", beforeSnapshot.lastAppliedLedgerEntryId())
            .addValue("afterLastAppliedLedgerEntryId", recoveredSnapshot.lastAppliedLedgerEntryId())
            .addValue("currencyCode", recoveredSnapshot.currencyCode())
            .addValue("recoveredBy", command.recoveredBy())
            .addValue("requestId", command.requestId())
            .addValue("recoveryReason", command.reason())
            .addValue("recoveredAt", Timestamp.from(recoveredAt)));
  }

  private MapSqlParameterSource driftParams(SnapshotComparison item, Instant observedAt) {
    return new MapSqlParameterSource()
        .addValue("accountId", item.accountId())
        .addValue("observedAt", Timestamp.from(observedAt))
        .addValue("snapshotAvailableBalanceMinor", item.snapshotAvailableBalanceMinor())
        .addValue("expectedAvailableBalanceMinor", item.expectedAvailableBalanceMinor())
        .addValue("snapshotPendingBalanceMinor", item.snapshotPendingBalanceMinor())
        .addValue("expectedPendingBalanceMinor", item.expectedPendingBalanceMinor())
        .addValue("snapshotLastAppliedLedgerEntryId", item.snapshotLastAppliedLedgerEntryId())
        .addValue("expectedLastAppliedLedgerEntryId", item.expectedLastAppliedLedgerEntryId())
        .addValue("currencyCode", item.currencyCode());
  }

  private static SnapshotComparison mapSnapshotComparison(ResultSet rs) throws SQLException {
    return new SnapshotComparison(
        rs.getLong("account_id"),
        rs.getLong("snapshot_available_balance_minor"),
        rs.getLong("expected_available_balance_minor"),
        rs.getLong("snapshot_pending_balance_minor"),
        rs.getLong("expected_pending_balance_minor"),
        rs.getLong("snapshot_last_applied_ledger_entry_id"),
        rs.getLong("expected_last_applied_ledger_entry_id"),
        rs.getString("currency_code"));
  }

  private static LedgerSnapshotDriftRecord mapDriftRecord(ResultSet rs) throws SQLException {
    OffsetDateTime observedAt = rs.getObject("observed_at", OffsetDateTime.class);
    return new LedgerSnapshotDriftRecord(
        rs.getLong("id"),
        rs.getLong("account_id"),
        observedAt.toInstant(),
        rs.getLong("snapshot_available_balance_minor"),
        rs.getLong("expected_available_balance_minor"),
        rs.getLong("snapshot_pending_balance_minor"),
        rs.getLong("expected_pending_balance_minor"),
        rs.getLong("snapshot_last_applied_ledger_entry_id"),
        rs.getLong("expected_last_applied_ledger_entry_id"),
        rs.getString("currency_code"),
        rs.getString("drift_status"));
  }

  private static LedgerSnapshotValues mapSnapshotValues(ResultSet rs) throws SQLException {
    return new LedgerSnapshotValues(
        rs.getLong("available_balance_minor"),
        rs.getLong("pending_balance_minor"),
        rs.getLong("last_applied_ledger_entry_id"),
        rs.getString("currency_code"));
  }

  private record SnapshotComparison(
      long accountId,
      long snapshotAvailableBalanceMinor,
      long expectedAvailableBalanceMinor,
      long snapshotPendingBalanceMinor,
      long expectedPendingBalanceMinor,
      long snapshotLastAppliedLedgerEntryId,
      long expectedLastAppliedLedgerEntryId,
      String currencyCode) {

    private boolean isDrifted() {
      return snapshotAvailableBalanceMinor != expectedAvailableBalanceMinor
          || snapshotPendingBalanceMinor != expectedPendingBalanceMinor
          || snapshotLastAppliedLedgerEntryId != expectedLastAppliedLedgerEntryId;
    }
  }
}
