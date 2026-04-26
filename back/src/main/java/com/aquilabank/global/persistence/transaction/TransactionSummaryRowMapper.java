package com.aquilabank.global.persistence.transaction;

import com.aquilabank.domain.transaction.model.TransactionDirection;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import com.aquilabank.domain.transaction.model.TransactionSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.RowMapper;

/** 1억 row read path에서 enum directory HashMap lookup을 피하는 bounded mapper */
final class TransactionSummaryRowMapper implements RowMapper<TransactionSummary> {

  static final TransactionSummaryRowMapper INSTANCE = new TransactionSummaryRowMapper();

  private TransactionSummaryRowMapper() {}

  @Override
  public TransactionSummary mapRow(ResultSet rs, int rowNum) throws SQLException {
    OffsetDateTime bookedAt = rs.getObject("booked_at", OffsetDateTime.class);
    return new TransactionSummary(
        rs.getLong("id"),
        rs.getLong("account_id"),
        rs.getString("transaction_reference"),
        direction(rs.getString("direction")),
        status(rs.getString("transaction_status")),
        rs.getLong("amount_minor"),
        rs.getLong("balance_after_minor"),
        rs.getString("currency_code"),
        rs.getString("summary"),
        rs.getString("counterparty_masked_name"),
        bookedAt.toInstant());
  }

  private static TransactionDirection direction(String value) throws SQLException {
    return switch (value) {
      case "DEBIT" -> TransactionDirection.DEBIT;
      case "CREDIT" -> TransactionDirection.CREDIT;
      default -> throw new SQLException("Unknown transaction direction: " + value);
    };
  }

  private static TransactionStatus status(String value) throws SQLException {
    return switch (value) {
      case "PENDING" -> TransactionStatus.PENDING;
      case "BOOKED" -> TransactionStatus.BOOKED;
      case "PARTIALLY_REVERSED" -> TransactionStatus.PARTIALLY_REVERSED;
      case "REVERSED" -> TransactionStatus.REVERSED;
      default -> throw new SQLException("Unknown transaction status: " + value);
    };
  }
}
