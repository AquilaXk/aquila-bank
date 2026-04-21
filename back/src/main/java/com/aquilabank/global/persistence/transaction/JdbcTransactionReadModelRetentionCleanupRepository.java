package com.aquilabank.global.persistence.transaction;

import com.aquilabank.domain.transaction.port.TransactionReadModelRetentionCleanupPort;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** transaction read model hot table retention을 작은 batch archive로 처리하는 JDBC adapter */
@Repository
public class JdbcTransactionReadModelRetentionCleanupRepository
    implements TransactionReadModelRetentionCleanupPort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcTransactionReadModelRetentionCleanupRepository(
      NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional
  public int archiveExpiredReadModels(Instant cutoff, int batchSize, Instant archivedAt) {
    // archive insert 성공이 확인된 row만 hot table에서 제거해 projection 유실 가능성을 막습니다.
    Integer archived =
        jdbcTemplate.queryForObject(
            """
            WITH expired AS (
                SELECT id,
                       ledger_entry_id,
                       account_id,
                       transaction_reference,
                       direction,
                       transaction_status,
                       amount_minor,
                       balance_after_minor,
                       currency_code,
                       summary,
                       counterparty_masked_name,
                       booked_at,
                       created_at
                FROM transaction_read_model
                WHERE booked_at < :cutoff
                FOR UPDATE SKIP LOCKED
                LIMIT :batchSize
            ),
            archived AS (
                INSERT INTO transaction_read_model_archive (
                    id,
                    ledger_entry_id,
                    account_id,
                    transaction_reference,
                    direction,
                    transaction_status,
                    amount_minor,
                    balance_after_minor,
                    currency_code,
                    summary,
                    counterparty_masked_name,
                    booked_at,
                    created_at,
                    archived_at
                )
                SELECT id,
                       ledger_entry_id,
                       account_id,
                       transaction_reference,
                       direction,
                       transaction_status,
                       amount_minor,
                       balance_after_minor,
                       currency_code,
                       summary,
                       counterparty_masked_name,
                       booked_at,
                       created_at,
                       :archivedAt
                FROM expired
                ON CONFLICT DO NOTHING
                RETURNING ledger_entry_id
            ),
            safe_to_delete AS (
                SELECT ledger_entry_id
                FROM archived
                UNION
                SELECT e.ledger_entry_id
                FROM expired e
                JOIN transaction_read_model_archive a
                  ON a.ledger_entry_id = e.ledger_entry_id
            ),
            deleted AS (
                DELETE FROM transaction_read_model t
                USING expired e
                JOIN safe_to_delete s
                  ON s.ledger_entry_id = e.ledger_entry_id
                WHERE t.id = e.id
                RETURNING t.id
            )
            SELECT COUNT(*)
            FROM deleted
            """,
            new MapSqlParameterSource()
                .addValue("cutoff", Timestamp.from(cutoff))
                .addValue("batchSize", batchSize)
                .addValue("archivedAt", Timestamp.from(archivedAt)),
            Integer.class);
    return archived == null ? 0 : archived;
  }
}
