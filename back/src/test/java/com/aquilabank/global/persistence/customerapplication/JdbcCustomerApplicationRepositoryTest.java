package com.aquilabank.global.persistence.customerapplication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.customerapplication.exception.CustomerApplicationConflictException;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStatus;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationSubmission;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationType;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationWriteCommand;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class JdbcCustomerApplicationRepositoryTest {

  @Test
  void submitsApplicationAndMapsReturnedRow() {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    JdbcCustomerApplicationRepository repository =
        new JdbcCustomerApplicationRepository(jdbcTemplate, new ObjectMapper());
    when(jdbcTemplate.query(
            anyString(),
            any(MapSqlParameterSource.class),
            ArgumentMatchers.<RowMapper<CustomerApplicationSubmission>>any()))
        .thenAnswer(
            invocation -> {
              RowMapper<CustomerApplicationSubmission> mapper = invocation.getArgument(2);
              return List.of(mapper.mapRow(resultSet(101L, false), 0));
            });

    CustomerApplicationSubmission result = repository.submit(command(101L));

    assertEquals("CSA-20260511-001", result.applicationReference());
    assertEquals(101L, result.accountId());
    assertEquals(CustomerApplicationType.BILL_PAYMENT, result.applicationType());
    assertEquals(CustomerApplicationStatus.SUBMITTED, result.status());
  }

  @Test
  void returnsConflictWhenIdempotencyKeyIsReusedWithDifferentFingerprint() {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    JdbcCustomerApplicationRepository repository =
        new JdbcCustomerApplicationRepository(jdbcTemplate, new ObjectMapper());
    when(jdbcTemplate.query(
            anyString(),
            any(MapSqlParameterSource.class),
            ArgumentMatchers.<RowMapper<CustomerApplicationSubmission>>any()))
        .thenReturn(List.of());

    assertThrows(
        CustomerApplicationConflictException.class, () -> repository.submit(command(null)));
  }

  @Test
  void wrapsJsonSerializationFailure() throws Exception {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    ObjectMapper objectMapper = mock(ObjectMapper.class);
    when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("boom") {});
    JdbcCustomerApplicationRepository repository =
        new JdbcCustomerApplicationRepository(jdbcTemplate, objectMapper);

    assertThrows(IllegalStateException.class, () -> repository.submit(command(null)));
  }

  private static CustomerApplicationWriteCommand command(Long accountId) {
    Instant now = Instant.parse("2026-05-11T03:00:00Z");
    return new CustomerApplicationWriteCommand(
        "CSA-20260511-001",
        7L,
        accountId,
        CustomerApplicationType.BILL_PAYMENT,
        CustomerApplicationStatus.SUBMITTED,
        "bill-001",
        "f".repeat(64),
        true,
        now,
        Map.of("billerCode", "GIRO"),
        now);
  }

  private static ResultSet resultSet(long accountId, boolean accountWasNull) throws Exception {
    Instant now = Instant.parse("2026-05-11T03:00:00Z");
    ResultSet rs = mock(ResultSet.class);
    when(rs.getString("application_reference")).thenReturn("CSA-20260511-001");
    when(rs.getLong("user_id")).thenReturn(7L);
    when(rs.getLong("account_id")).thenReturn(accountId);
    when(rs.wasNull()).thenReturn(accountWasNull);
    when(rs.getString("application_type")).thenReturn("BILL_PAYMENT");
    when(rs.getString("application_status")).thenReturn("SUBMITTED");
    when(rs.getBoolean("mfa_verified")).thenReturn(true);
    when(rs.getTimestamp("mfa_verified_at")).thenReturn(Timestamp.from(now));
    when(rs.getTimestamp("submitted_at")).thenReturn(Timestamp.from(now));
    when(rs.getTimestamp("updated_at")).thenReturn(Timestamp.from(now));
    return rs;
  }
}
