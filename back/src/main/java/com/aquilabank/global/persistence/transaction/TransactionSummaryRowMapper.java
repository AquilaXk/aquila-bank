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

  private static final int COL_ID = 1;
  private static final int COL_ACCOUNT_ID = 2;
  private static final int COL_TRANSACTION_REFERENCE = 3;
  private static final int COL_DIRECTION = 4;
  private static final int COL_TRANSACTION_STATUS = 5;
  private static final int COL_AMOUNT_MINOR = 6;
  private static final int COL_BALANCE_AFTER_MINOR = 7;
  private static final int COL_CURRENCY_CODE = 8;
  private static final int COL_SUMMARY = 9;
  private static final int COL_COUNTERPARTY_MASKED_NAME = 10;
  private static final int COL_BOOKED_AT = 11;

  private TransactionSummaryRowMapper() {}

  @Override
  public TransactionSummary mapRow(ResultSet rs, int rowNum) throws SQLException {
    OffsetDateTime bookedAt = rs.getObject(COL_BOOKED_AT, OffsetDateTime.class);
    return new TransactionSummary(
        rs.getLong(COL_ID),
        rs.getLong(COL_ACCOUNT_ID),
        rs.getString(COL_TRANSACTION_REFERENCE),
        direction(rs.getString(COL_DIRECTION)),
        status(rs.getString(COL_TRANSACTION_STATUS)),
        rs.getLong(COL_AMOUNT_MINOR),
        rs.getLong(COL_BALANCE_AFTER_MINOR),
        rs.getString(COL_CURRENCY_CODE),
        rs.getString(COL_SUMMARY),
        rs.getString(COL_COUNTERPARTY_MASKED_NAME),
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
