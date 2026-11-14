package com.aquilabank.global.persistence.ledger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.ledger.model.TransferCommand;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class JdbcTransferWriteRepositoryTest {

  @Test
  void failsClosedWhenAccountingBundleDoesNotUpdateBothBalanceSnapshots() {
    assertThrowsOnSnapshotUpdateCount(1);
    assertThrowsOnSnapshotUpdateCount(null);
  }

  @SuppressWarnings("unchecked")
  private void assertThrowsOnSnapshotUpdateCount(Integer updatedSnapshotCount) {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
        .thenAnswer(
            invocation -> {
              String sql = invocation.getArgument(0, String.class);
              RowMapper<Object> rowMapper = invocation.getArgument(2, RowMapper.class);
              if (sql.contains("FROM command_idempotency")) {
                return List.of();
              }
              return List.of(
                  rowMapper.mapRow(mockSnapshotRow(1L, 5_000L), 0),
                  rowMapper.mapRow(mockSnapshotRow(2L, 1_000L), 1));
            });
    when(jdbcTemplate.update(anyString(), any(SqlParameterSource.class))).thenReturn(1);
    when(jdbcTemplate.queryForObject(anyString(), any(SqlParameterSource.class), eq(Integer.class)))
        .thenReturn(updatedSnapshotCount);

    JdbcTransferWriteRepository repository =
        new JdbcTransferWriteRepository(jdbcTemplate, new ObjectMapper());
    TransferCommand command = new TransferCommand(1L, 2L, 1_000L, "KRW", "coverage", "idem-err");

    assertThatThrownBy(() -> repository.transfer(command))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("balance snapshot update failed");
  }

  private ResultSet mockSnapshotRow(long accountId, long balanceMinor) throws SQLException {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getLong("account_id")).thenReturn(accountId);
    when(rs.getLong("last_applied_ledger_entry_id")).thenReturn(10L);
    when(rs.getLong("available_balance_minor")).thenReturn(balanceMinor);
    when(rs.getLong("pending_balance_minor")).thenReturn(0L);
    when(rs.getString("currency_code")).thenReturn("KRW");
    when(rs.getString("account_status")).thenReturn("ACTIVE");
    return rs;
  }
}
