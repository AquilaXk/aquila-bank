package com.aquilabank.global.persistence.transaction;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.domain.transaction.model.TransactionDirection;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import com.aquilabank.domain.transaction.model.TransactionSummary;
import com.aquilabank.domain.transaction.port.TransactionReadPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** JDBC read adapter optimized for account timeline lookup via keyset pagination. */
@Repository
public class JdbcTransactionReadRepository implements TransactionReadPort {

  private static final RowMapper<TransactionSummary> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcTransactionReadRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional(readOnly = true)
  public TransactionSlice fetch(TransactionQuery query) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("accountId", query.accountId())
            .addValue("from", Timestamp.from(query.from()))
            .addValue("to", Timestamp.from(query.to()))
            // Fetch one extra row to tell the API whether a next page exists.
            .addValue("fetchLimit", query.limit() + 1);

    StringBuilder sql =
        new StringBuilder(
            """
            SELECT id,
                   account_id,
                   transaction_reference,
                   direction,
                   transaction_status,
                   amount_minor,
                   balance_after_minor,
                   currency_code,
                   summary,
                   counterparty_masked_name,
                   booked_at
            FROM transaction_read_model
            WHERE account_id = :accountId
              AND booked_at >= :from
              AND booked_at < :to
            """);

    if (query.status() != null) {
      sql.append("\n  AND transaction_status = :status");
      params.addValue("status", query.status().name());
    }

    if (query.cursor() != null) {
      // Keyset pagination reuses the same ORDER BY columns to avoid deep OFFSET scans.
      sql.append(
          """

              AND (
                    booked_at < :cursorBookedAt
                 OR (booked_at = :cursorBookedAt AND id < :cursorId)
              )
          """);
      params
          .addValue("cursorBookedAt", Timestamp.from(query.cursor().bookedAt()))
          .addValue("cursorId", query.cursor().id());
    }

    sql.append(
        """

            ORDER BY booked_at DESC, id DESC
            LIMIT :fetchLimit
        """);

    List<TransactionSummary> rows = jdbcTemplate.query(sql.toString(), params, ROW_MAPPER);
    boolean hasNext = rows.size() > query.limit();
    // Trim the sentinel row before returning the slice.
    List<TransactionSummary> items =
        hasNext ? new ArrayList<>(rows.subList(0, query.limit())) : rows;
    TransactionCursor nextCursor = hasNext ? toCursor(items.getLast()) : null;
    return new TransactionSlice(items, nextCursor, hasNext, query.limit());
  }

  private static TransactionSummary mapRow(ResultSet rs) throws SQLException {
    OffsetDateTime bookedAt = rs.getObject("booked_at", OffsetDateTime.class);
    return new TransactionSummary(
        rs.getLong("id"),
        rs.getLong("account_id"),
        rs.getString("transaction_reference"),
        TransactionDirection.valueOf(rs.getString("direction")),
        TransactionStatus.valueOf(rs.getString("transaction_status")),
        rs.getLong("amount_minor"),
        rs.getLong("balance_after_minor"),
        rs.getString("currency_code"),
        rs.getString("summary"),
        rs.getString("counterparty_masked_name"),
        bookedAt.toInstant());
  }

  private static TransactionCursor toCursor(TransactionSummary item) {
    // The next page starts strictly after the last visible row in the current slice.
    return new TransactionCursor(item.bookedAt(), item.id());
  }
}
