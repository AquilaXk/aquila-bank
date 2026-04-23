package com.aquilabank.global.persistence.transaction;

import com.aquilabank.domain.transaction.model.TransactionDetail;
import com.aquilabank.domain.transaction.model.TransactionDetailQuery;
import com.aquilabank.domain.transaction.model.TransactionDirection;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import com.aquilabank.domain.transaction.port.TransactionDetailReadPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 거래 상세 exact lookup을 transaction_read_model + ledger_entry join으로 읽어옵니다. */
@Repository
public class JdbcTransactionDetailRepository implements TransactionDetailReadPort {

  private static final RowMapper<TransactionDetail> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcTransactionDetailRepository(
      @Qualifier("transactionReadJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional(readOnly = true, transactionManager = "transactionReadTransactionManager")
  public Optional<TransactionDetail> find(TransactionDetailQuery query) {
    TransactionDetailQueryStatement statement = TransactionDetailQueryStatement.from(query);
    List<TransactionDetail> rows =
        jdbcTemplate.query(statement.sql(), statement.params(), ROW_MAPPER);
    if (rows.size() > 1) {
      throw new IllegalStateException(
          "transaction detail query returned multiple rows for accountId=%d transactionReference=%s"
              .formatted(query.accountId(), query.transactionReference()));
    }
    return rows.stream().findFirst();
  }

  private static TransactionDetail mapRow(ResultSet rs) throws SQLException {
    return new TransactionDetail(
        rs.getLong("account_id"),
        rs.getString("transaction_reference"),
        TransactionDirection.valueOf(rs.getString("direction")),
        TransactionStatus.valueOf(rs.getString("transaction_status")),
        rs.getLong("amount_minor"),
        rs.getLong("balance_after_minor"),
        rs.getString("currency_code"),
        rs.getString("summary"),
        rs.getString("counterparty_masked_name"),
        toInstant(rs.getTimestamp("booked_at"), "booked_at"),
        rs.getString("entry_reference"),
        TransactionStatus.valueOf(rs.getString("entry_status")),
        toInstant(rs.getTimestamp("occurred_at"), "occurred_at"),
        rs.getString("description"));
  }

  private static Instant toInstant(Timestamp timestamp, String columnName) {
    if (timestamp == null) {
      throw new IllegalStateException(columnName + " must not be null");
    }
    return timestamp.toInstant();
  }
}
