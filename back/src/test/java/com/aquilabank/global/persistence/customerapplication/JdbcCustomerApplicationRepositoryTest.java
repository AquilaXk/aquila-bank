package com.aquilabank.global.persistence.customerapplication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.customerapplication.exception.CustomerApplicationConflictException;
import com.aquilabank.domain.customerapplication.exception.CustomerApplicationNotFoundException;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationAction;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationOperationAuditEntry;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStateUpdateCommand;
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
import java.util.Set;
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
  void findsApplicationByReferenceForUpdateAndMapsPayload() {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    JdbcCustomerApplicationRepository repository =
        new JdbcCustomerApplicationRepository(jdbcTemplate, new ObjectMapper());
    when(jdbcTemplate.query(
            anyString(),
            any(MapSqlParameterSource.class),
            ArgumentMatchers.<RowMapper<CustomerApplicationDetails>>any()))
        .thenAnswer(
            invocation -> {
              RowMapper<CustomerApplicationDetails> mapper = invocation.getArgument(2);
              return List.of(mapper.mapRow(detailsResultSet("{}", false), 0));
            });

    CustomerApplicationDetails result =
        repository.findByReferenceForUpdate("CSA-20260511-001").orElseThrow();

    assertEquals(CustomerApplicationStatus.APPROVED, result.status());
    assertEquals("GIRO", result.payload().get("billerCode"));
    assertEquals(Map.of(), result.executionResult());
  }

  @Test
  void mapsBlankExecutionResultAsEmptyPayload() {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    JdbcCustomerApplicationRepository repository =
        new JdbcCustomerApplicationRepository(jdbcTemplate, new ObjectMapper());
    when(jdbcTemplate.query(
            anyString(),
            any(MapSqlParameterSource.class),
            ArgumentMatchers.<RowMapper<CustomerApplicationDetails>>any()))
        .thenAnswer(
            invocation -> {
              RowMapper<CustomerApplicationDetails> mapper = invocation.getArgument(2);
              return List.of(mapper.mapRow(detailsResultSet("", false), 0));
            });

    CustomerApplicationDetails result =
        repository.findByReferenceForUpdate("CSA-20260511-001").orElseThrow();

    assertEquals(Map.of(), result.executionResult());
  }

  @Test
  void findsApplicationsByUserIdWithLimit() {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    JdbcCustomerApplicationRepository repository =
        new JdbcCustomerApplicationRepository(jdbcTemplate, new ObjectMapper());
    when(jdbcTemplate.query(
            anyString(),
            any(MapSqlParameterSource.class),
            ArgumentMatchers.<RowMapper<CustomerApplicationDetails>>any()))
        .thenAnswer(
            invocation -> {
              RowMapper<CustomerApplicationDetails> mapper = invocation.getArgument(2);
              return List.of(mapper.mapRow(detailsResultSet("{}", false), 0));
            });

    List<CustomerApplicationDetails> result = repository.findByUserId(7L, 20);

    assertEquals(1, result.size());
    assertEquals("CSA-20260511-001", result.getFirst().applicationReference());
  }

  @Test
  void findsApplicationByUserIdAndReference() {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    JdbcCustomerApplicationRepository repository =
        new JdbcCustomerApplicationRepository(jdbcTemplate, new ObjectMapper());
    when(jdbcTemplate.query(
            anyString(),
            any(MapSqlParameterSource.class),
            ArgumentMatchers.<RowMapper<CustomerApplicationDetails>>any()))
        .thenAnswer(
            invocation -> {
              RowMapper<CustomerApplicationDetails> mapper = invocation.getArgument(2);
              return List.of(mapper.mapRow(detailsResultSet("{}", false), 0));
            });

    CustomerApplicationDetails result =
        repository.findByUserIdAndReference(7L, "CSA-20260511-001").orElseThrow();

    assertEquals(7L, result.userId());
  }

  @Test
  void updatesApplicationStatusAndMapsExecutionResult() {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    JdbcCustomerApplicationRepository repository =
        new JdbcCustomerApplicationRepository(jdbcTemplate, new ObjectMapper());
    when(jdbcTemplate.query(
            anyString(),
            any(MapSqlParameterSource.class),
            ArgumentMatchers.<RowMapper<CustomerApplicationDetails>>any()))
        .thenAnswer(
            invocation -> {
              RowMapper<CustomerApplicationDetails> mapper = invocation.getArgument(2);
              return List.of(mapper.mapRow(detailsResultSet("{\"executed\":true}", false), 0));
            });

    CustomerApplicationDetails result =
        repository.updateStatus(
            new CustomerApplicationStateUpdateCommand(
                "CSA-20260511-001",
                CustomerApplicationStatus.EXECUTED,
                "done",
                "ops",
                Instant.parse("2026-05-11T03:10:00Z"),
                Map.of("executed", true)));

    assertEquals(CustomerApplicationStatus.APPROVED, result.status());
    assertEquals(true, result.executionResult().get("executed"));
  }

  @Test
  void updateStatusReturnsNotFoundWhenReferenceIsMissing() {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    JdbcCustomerApplicationRepository repository =
        new JdbcCustomerApplicationRepository(jdbcTemplate, new ObjectMapper());
    when(jdbcTemplate.query(
            anyString(),
            any(MapSqlParameterSource.class),
            ArgumentMatchers.<RowMapper<CustomerApplicationDetails>>any()))
        .thenReturn(List.of());

    assertThrows(
        CustomerApplicationNotFoundException.class,
        () ->
            repository.updateStatus(
                new CustomerApplicationStateUpdateCommand(
                    "CSA-missing",
                    CustomerApplicationStatus.REJECTED,
                    "missing",
                    "ops",
                    Instant.parse("2026-05-11T03:10:00Z"),
                    Map.of())));
  }

  @Test
  void appendsOperationAuditEntry() {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    JdbcCustomerApplicationRepository repository =
        new JdbcCustomerApplicationRepository(jdbcTemplate, new ObjectMapper());

    repository.append(
        new CustomerApplicationOperationAuditEntry(
            "CSA-20260511-001",
            CustomerApplicationAction.EXECUTE,
            CustomerApplicationStatus.APPROVED,
            CustomerApplicationStatus.FAILED,
            "ops-executor",
            "EXTERNAL_EXECUTION_NOT_CONFIGURED",
            "req-execute-001",
            Instant.parse("2026-05-11T03:10:00Z"),
            Map.of("applicationType", "BILL_PAYMENT")));

    verify(jdbcTemplate).update(anyString(), any(MapSqlParameterSource.class));
  }

  @Test
  void checksOperationAuditHistoryByReferenceActorAndActions() {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    JdbcCustomerApplicationRepository repository =
        new JdbcCustomerApplicationRepository(jdbcTemplate, new ObjectMapper());
    when(jdbcTemplate.queryForObject(
            anyString(), any(MapSqlParameterSource.class), ArgumentMatchers.eq(Boolean.class)))
        .thenReturn(true);

    boolean result =
        repository.existsByReferenceAndActorAndActions(
            "CSA-20260511-001",
            "ops-reviewer",
            Set.of(CustomerApplicationAction.START_REVIEW, CustomerApplicationAction.APPROVE));

    assertEquals(true, result);
  }

  @Test
  void wrapsJsonDeserializationFailure() {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    JdbcCustomerApplicationRepository repository =
        new JdbcCustomerApplicationRepository(jdbcTemplate, new ObjectMapper());
    when(jdbcTemplate.query(
            anyString(),
            any(MapSqlParameterSource.class),
            ArgumentMatchers.<RowMapper<CustomerApplicationDetails>>any()))
        .thenAnswer(
            invocation -> {
              RowMapper<CustomerApplicationDetails> mapper = invocation.getArgument(2);
              return List.of(mapper.mapRow(detailsResultSet("{", false), 0));
            });

    assertThrows(
        IllegalStateException.class, () -> repository.findByReferenceForUpdate("CSA-20260511-001"));
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

  private static ResultSet detailsResultSet(String executionResult, boolean accountWasNull)
      throws Exception {
    Instant now = Instant.parse("2026-05-11T03:00:00Z");
    ResultSet rs = mock(ResultSet.class);
    when(rs.getString("application_reference")).thenReturn("CSA-20260511-001");
    when(rs.getLong("user_id")).thenReturn(7L);
    when(rs.getLong("account_id")).thenReturn(101L);
    when(rs.wasNull()).thenReturn(accountWasNull);
    when(rs.getString("application_type")).thenReturn("BILL_PAYMENT");
    when(rs.getString("application_status")).thenReturn("APPROVED");
    when(rs.getBoolean("mfa_verified")).thenReturn(true);
    when(rs.getTimestamp("mfa_verified_at")).thenReturn(Timestamp.from(now));
    when(rs.getString("payload")).thenReturn("{\"billerCode\":\"GIRO\"}");
    when(rs.getTimestamp("submitted_at")).thenReturn(Timestamp.from(now));
    when(rs.getTimestamp("updated_at")).thenReturn(Timestamp.from(now));
    when(rs.getString("status_reason")).thenReturn("done");
    when(rs.getString("processed_by")).thenReturn("ops");
    when(rs.getTimestamp("processed_at")).thenReturn(Timestamp.from(now));
    when(rs.getString("execution_result")).thenReturn(executionResult);
    return rs;
  }
}
