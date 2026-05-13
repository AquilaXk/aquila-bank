package com.aquilabank.global.persistence.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.customerapplication.model.CustomerTransferLimitPolicyCommand;
import com.aquilabank.domain.ledger.model.TransferLimitPolicy;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class JdbcTransferLimitPolicyOverrideRepositoryTest {

  @Test
  void findsOverridePolicyByAccountId() {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    JdbcTransferLimitPolicyOverrideRepository repository =
        new JdbcTransferLimitPolicyOverrideRepository(jdbcTemplate);
    when(jdbcTemplate.query(
            anyString(),
            any(MapSqlParameterSource.class),
            ArgumentMatchers.<RowMapper<TransferLimitPolicy>>any()))
        .thenAnswer(
            invocation -> {
              RowMapper<TransferLimitPolicy> mapper = invocation.getArgument(2);
              return List.of(mapper.mapRow(resultSet(), 0));
            });

    assertThat(repository.findByAccountId(101L))
        .contains(new TransferLimitPolicy(500_000L, 2_000_000L));
  }

  @Test
  void appliesTransferLimitChangeWithUpsert() {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    JdbcTransferLimitPolicyOverrideRepository repository =
        new JdbcTransferLimitPolicyOverrideRepository(jdbcTemplate);
    when(jdbcTemplate.query(
            anyString(),
            any(MapSqlParameterSource.class),
            ArgumentMatchers.<RowMapper<TransferLimitPolicy>>any()))
        .thenAnswer(
            invocation -> {
              RowMapper<TransferLimitPolicy> mapper = invocation.getArgument(2);
              return List.of(mapper.mapRow(resultSet(), 0));
            });

    TransferLimitPolicy result =
        repository.applyTransferLimitChange(
            new CustomerTransferLimitPolicyCommand(
                7L,
                101L,
                500_000L,
                2_000_000L,
                "CSA-001",
                "ops-executor",
                "req-transfer-limit",
                Instant.parse("2026-05-13T03:00:00Z")));

    assertThat(result).isEqualTo(new TransferLimitPolicy(500_000L, 2_000_000L));
  }

  @Test
  void failsWhenTransferLimitChangeUpsertReturnsNoRow() {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    JdbcTransferLimitPolicyOverrideRepository repository =
        new JdbcTransferLimitPolicyOverrideRepository(jdbcTemplate);
    when(jdbcTemplate.query(
            anyString(),
            any(MapSqlParameterSource.class),
            ArgumentMatchers.<RowMapper<TransferLimitPolicy>>any()))
        .thenReturn(List.of());

    assertThatThrownBy(
            () ->
                repository.applyTransferLimitChange(
                    new CustomerTransferLimitPolicyCommand(
                        7L,
                        101L,
                        500_000L,
                        2_000_000L,
                        "CSA-001",
                        "ops-executor",
                        "req-transfer-limit",
                        Instant.parse("2026-05-13T03:00:00Z"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("transfer limit policy upsert returned no row");
  }

  private static ResultSet resultSet() throws Exception {
    ResultSet rs = mock(ResultSet.class);
    when(rs.getLong("single_transfer_limit_minor")).thenReturn(500_000L);
    when(rs.getLong("daily_transfer_limit_minor")).thenReturn(2_000_000L);
    return rs;
  }
}
