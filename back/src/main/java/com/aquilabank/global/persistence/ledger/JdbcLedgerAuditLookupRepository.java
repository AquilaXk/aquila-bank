package com.aquilabank.global.persistence.ledger;

import com.aquilabank.domain.ledger.model.LedgerAuditEntry;
import com.aquilabank.domain.ledger.port.LedgerAuditLookupPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** ledger_entry 원본 감사 추적을 index 기반 bounded lookup으로 제한합니다. */
@Repository
public class JdbcLedgerAuditLookupRepository implements LedgerAuditLookupPort {

  private static final RowMapper<LedgerAuditEntry> ROW_MAPPER =
      (rs, rowNum) -> mapLedgerAuditEntry(rs);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcLedgerAuditLookupRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional(readOnly = true)
  public List<LedgerAuditEntry> findByRequestId(String requestId, long afterEntryId, int limit) {
    return jdbcTemplate.query(
        """
        SELECT id,
               account_id,
               transaction_reference,
               entry_reference,
               direction,
               entry_status,
               amount_minor,
               currency_code,
               booked_at,
               occurred_at,
               description,
               trace_id,
               created_at
        FROM ledger_entry
        WHERE trace_id = :requestId
          AND id > :afterEntryId
        ORDER BY id ASC
        LIMIT :limit
        """,
        new MapSqlParameterSource()
            .addValue("requestId", requestId)
            .addValue("afterEntryId", afterEntryId)
            .addValue("limit", limit),
        ROW_MAPPER);
  }

  @Override
  @Transactional(readOnly = true)
  public List<LedgerAuditEntry> findByTransactionReference(
      String transactionReference, long afterEntryId, int limit) {
    return jdbcTemplate.query(
        """
        SELECT id,
               account_id,
               transaction_reference,
               entry_reference,
               direction,
               entry_status,
               amount_minor,
               currency_code,
               booked_at,
               occurred_at,
               description,
               trace_id,
               created_at
        FROM ledger_entry
        WHERE transaction_reference = :transactionReference
          AND id > :afterEntryId
        ORDER BY id ASC
        LIMIT :limit
        """,
        new MapSqlParameterSource()
            .addValue("transactionReference", transactionReference)
            .addValue("afterEntryId", afterEntryId)
            .addValue("limit", limit),
        ROW_MAPPER);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<LedgerAuditEntry> findByEntryReference(String entryReference) {
    return jdbcTemplate
        .query(
            """
            SELECT id,
                   account_id,
                   transaction_reference,
                   entry_reference,
                   direction,
                   entry_status,
                   amount_minor,
                   currency_code,
                   booked_at,
                   occurred_at,
                   description,
                   trace_id,
                   created_at
            FROM ledger_entry
            WHERE entry_reference = :entryReference
            """,
            new MapSqlParameterSource().addValue("entryReference", entryReference),
            ROW_MAPPER)
        .stream()
        .findFirst();
  }

  private static LedgerAuditEntry mapLedgerAuditEntry(ResultSet rs) throws SQLException {
    return new LedgerAuditEntry(
        rs.getLong("id"),
        rs.getLong("account_id"),
        rs.getString("transaction_reference"),
        rs.getString("entry_reference"),
        rs.getString("direction"),
        rs.getString("entry_status"),
        rs.getLong("amount_minor"),
        rs.getString("currency_code"),
        rs.getObject("booked_at", OffsetDateTime.class).toInstant(),
        rs.getObject("occurred_at", OffsetDateTime.class).toInstant(),
        rs.getString("description"),
        rs.getString("trace_id"),
        rs.getObject("created_at", OffsetDateTime.class).toInstant());
  }
}
