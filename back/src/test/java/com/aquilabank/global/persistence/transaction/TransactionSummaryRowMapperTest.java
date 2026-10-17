package com.aquilabank.global.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.transaction.model.TransactionDirection;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import com.aquilabank.domain.transaction.model.TransactionSummary;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class TransactionSummaryRowMapperTest {

  @Test
  void mapsTransactionSummaryUsingPositionalColumnsWithoutStringOrEnumLookup() throws Exception {
    ResultSet rs = resultSet("CREDIT", "BOOKED");

    TransactionSummary item = TransactionSummaryRowMapper.INSTANCE.mapRow(rs, 0);

    assertThat(item.id()).isEqualTo(11L);
    assertThat(item.accountId()).isEqualTo(101L);
    assertThat(item.transactionReference()).isEqualTo("TRX-11");
    assertThat(item.direction()).isEqualTo(TransactionDirection.CREDIT);
    assertThat(item.status()).isEqualTo(TransactionStatus.BOOKED);
    assertThat(item.amountMinor()).isEqualTo(12_300L);
    assertThat(item.balanceAfterMinor()).isEqualTo(45_600L);
    assertThat(item.currencyCode()).isEqualTo("KRW");
    assertThat(item.summary()).isEqualTo("salary");
    assertThat(item.counterpartyMaskedName()).isEqualTo("A***");
    assertThat(item.bookedAt()).isEqualTo(OffsetDateTime.parse("2026-04-26T10:15:30Z").toInstant());
    verify(rs, never()).getLong(anyString());
    verify(rs, never()).getString(anyString());
    verify(rs, never()).getObject(anyString(), eq(OffsetDateTime.class));

    assertThat(TransactionSummaryRowMapper.INSTANCE.mapRow(resultSet("DEBIT", "PENDING"), 1))
        .extracting(TransactionSummary::direction, TransactionSummary::status)
        .containsExactly(TransactionDirection.DEBIT, TransactionStatus.PENDING);
    assertThat(
            TransactionSummaryRowMapper.INSTANCE.mapRow(
                resultSet("DEBIT", "PARTIALLY_REVERSED"), 2))
        .extracting(TransactionSummary::status)
        .isEqualTo(TransactionStatus.PARTIALLY_REVERSED);
    assertThat(TransactionSummaryRowMapper.INSTANCE.mapRow(resultSet("CREDIT", "REVERSED"), 3))
        .extracting(TransactionSummary::status)
        .isEqualTo(TransactionStatus.REVERSED);
  }

  @Test
  void rejectsUnknownDirection() throws Exception {
    ResultSet rs = resultSet("UNKNOWN", "BOOKED");

    assertThatThrownBy(() -> TransactionSummaryRowMapper.INSTANCE.mapRow(rs, 0))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("Unknown transaction direction");
  }

  @Test
  void rejectsUnknownStatus() throws Exception {
    ResultSet rs = resultSet("CREDIT", "UNKNOWN");

    assertThatThrownBy(() -> TransactionSummaryRowMapper.INSTANCE.mapRow(rs, 0))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("Unknown transaction status");
  }

  private static ResultSet resultSet(String direction, String status) throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getLong(1)).thenReturn(11L);
    when(rs.getLong(2)).thenReturn(101L);
    when(rs.getString(3)).thenReturn("TRX-11");
    when(rs.getString(4)).thenReturn(direction);
    when(rs.getString(5)).thenReturn(status);
    when(rs.getLong(6)).thenReturn(12_300L);
    when(rs.getLong(7)).thenReturn(45_600L);
    when(rs.getString(8)).thenReturn("KRW");
    when(rs.getString(9)).thenReturn("salary");
    when(rs.getString(10)).thenReturn("A***");
    when(rs.getObject(11, OffsetDateTime.class))
        .thenReturn(OffsetDateTime.parse("2026-04-26T10:15:30Z"));
    return rs;
  }
}
