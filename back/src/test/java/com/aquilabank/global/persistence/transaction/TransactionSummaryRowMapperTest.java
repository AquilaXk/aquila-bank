package com.aquilabank.global.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
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
  void mapsTransactionSummaryWithoutEnumValueOfLookup() throws Exception {
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
    when(rs.getLong("id")).thenReturn(11L);
    when(rs.getLong("account_id")).thenReturn(101L);
    when(rs.getString("transaction_reference")).thenReturn("TRX-11");
    when(rs.getString("direction")).thenReturn(direction);
    when(rs.getString("transaction_status")).thenReturn(status);
    when(rs.getLong("amount_minor")).thenReturn(12_300L);
    when(rs.getLong("balance_after_minor")).thenReturn(45_600L);
    when(rs.getString("currency_code")).thenReturn("KRW");
    when(rs.getString("summary")).thenReturn("salary");
    when(rs.getString("counterparty_masked_name")).thenReturn("A***");
    when(rs.getObject("booked_at", OffsetDateTime.class))
        .thenReturn(OffsetDateTime.parse("2026-04-26T10:15:30Z"));
    return rs;
  }
}
