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
    List<TransactionDetail> rows = queryRows(TransactionDetailQueryStatement.from(query));
    if (rows.isEmpty()) {
      rows = queryRows(TransactionDetailQueryStatement.fromArchive(query));
    }
    if (rows.size() > 1) {
      throw new IllegalStateException(
          "transaction detail query returned multiple rows for accountId=%d transactionReference=%s"
              .formatted(query.accountId(), query.transactionReference()));
    }
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
  }

  private List<TransactionDetail> queryRows(TransactionDetailQueryStatement statement) {
    return jdbcTemplate.query(statement.sql(), statement.params(), ROW_MAPPER);
  }

  private static TransactionDetail mapRow(ResultSet rs) throws SQLException {
    return new TransactionDetail(
        rs.getLong(1),
        rs.getString(2),
        TransactionDirection.valueOf(rs.getString(3)),
        TransactionStatus.valueOf(rs.getString(4)),
        rs.getLong(5),
        rs.getLong(6),
        rs.getString(7),
        rs.getString(8),
        rs.getString(9),
        toInstant(rs.getTimestamp(10), "booked_at"),
        rs.getString(11),
        TransactionStatus.valueOf(rs.getString(12)),
        toInstant(rs.getTimestamp(13), "occurred_at"),
        rs.getString(14));
  }

  private static Instant toInstant(Timestamp timestamp, String columnName) {
    if (timestamp == null) {
      throw new IllegalStateException(columnName + " must not be null");
    }
    return timestamp.toInstant();
  }
}
