package com.aquilabank.global.persistence.customerapplication;

import com.aquilabank.domain.customerapplication.exception.CustomerApplicationConflictException;
import com.aquilabank.domain.customerapplication.exception.CustomerApplicationNotFoundException;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStateUpdateCommand;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationStatus;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationSubmission;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationType;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationWriteCommand;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationOperationPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationReadPort;
import com.aquilabank.domain.customerapplication.port.CustomerApplicationWritePort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** 고객 신청 접수 write 모델을 idempotency key 기준으로 한 번만 저장합니다. */
@Repository
public class JdbcCustomerApplicationRepository
    implements CustomerApplicationWritePort,
        CustomerApplicationOperationPort,
        CustomerApplicationReadPort {

  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public JdbcCustomerApplicationRepository(
      NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  public CustomerApplicationSubmission submit(CustomerApplicationWriteCommand command) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("applicationReference", command.reference())
            .addValue("userId", command.userId())
            .addValue("accountId", command.accountId())
            .addValue("applicationType", command.applicationType().name())
            .addValue("applicationStatus", command.status().name())
            .addValue("idempotencyKey", command.idempotencyKey())
            .addValue("requestFingerprint", command.requestFingerprint())
            .addValue("mfaVerified", command.mfaVerified())
            .addValue(
                "mfaVerifiedAt",
                command.mfaVerifiedAt() == null ? null : Timestamp.from(command.mfaVerifiedAt()))
            .addValue("payload", toJson(command.payload()))
            .addValue("submittedAt", Timestamp.from(command.submittedAt()));

    return jdbcTemplate
        .query(
            """
            INSERT INTO customer_service_application (
                application_reference,
                user_id,
                account_id,
                application_type,
                application_status,
                idempotency_key,
                request_fingerprint,
                mfa_verified,
                mfa_verified_at,
                payload,
                submitted_at,
                updated_at
            )
            VALUES (
                :applicationReference,
                :userId,
                :accountId,
                :applicationType,
                :applicationStatus,
                :idempotencyKey,
                :requestFingerprint,
                :mfaVerified,
                :mfaVerifiedAt,
                CAST(:payload AS jsonb),
                :submittedAt,
                :submittedAt
            )
            ON CONFLICT (user_id, idempotency_key)
            DO UPDATE
            SET updated_at = customer_service_application.updated_at
            WHERE customer_service_application.request_fingerprint = :requestFingerprint
            RETURNING application_reference,
                      user_id,
                      account_id,
                      application_type,
                      application_status,
                      mfa_verified,
                      mfa_verified_at,
                      submitted_at,
                      updated_at
            """,
            params,
            (rs, rowNum) -> mapSubmission(rs, rowNum))
        .stream()
        .findFirst()
        .orElseThrow(
            () ->
                new CustomerApplicationConflictException(
                    "idempotency key is already used by a different application request"));
  }

  @Override
  public Optional<CustomerApplicationDetails> findByReferenceForUpdate(
      String applicationReference) {
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("applicationReference", applicationReference);
    return jdbcTemplate
        .query(
            """
            SELECT application_reference,
                   user_id,
                   account_id,
                   application_type,
                   application_status,
                   mfa_verified,
                   mfa_verified_at,
                   payload,
                   submitted_at,
                   updated_at,
                   status_reason,
                   processed_by,
                   processed_at,
                   execution_result
            FROM customer_service_application
            WHERE application_reference = :applicationReference
            FOR UPDATE
            """,
            params,
            (rs, rowNum) -> mapDetails(rs))
        .stream()
        .findFirst();
  }

  @Override
  public List<CustomerApplicationDetails> findByUserId(long userId, int limit) {
    MapSqlParameterSource params =
        new MapSqlParameterSource().addValue("userId", userId).addValue("limit", limit);
    return jdbcTemplate.query(
        """
        SELECT application_reference,
               user_id,
               account_id,
               application_type,
               application_status,
               mfa_verified,
               mfa_verified_at,
               payload,
               submitted_at,
               updated_at,
               status_reason,
               processed_by,
               processed_at,
               execution_result
        FROM customer_service_application
        WHERE user_id = :userId
        ORDER BY submitted_at DESC, id DESC
        LIMIT :limit
        """,
        params,
        (rs, rowNum) -> mapDetails(rs));
  }

  @Override
  public Optional<CustomerApplicationDetails> findByUserIdAndReference(
      long userId, String applicationReference) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("applicationReference", applicationReference);
    return jdbcTemplate
        .query(
            """
            SELECT application_reference,
                   user_id,
                   account_id,
                   application_type,
                   application_status,
                   mfa_verified,
                   mfa_verified_at,
                   payload,
                   submitted_at,
                   updated_at,
                   status_reason,
                   processed_by,
                   processed_at,
                   execution_result
            FROM customer_service_application
            WHERE user_id = :userId
              AND application_reference = :applicationReference
            """,
            params,
            (rs, rowNum) -> mapDetails(rs))
        .stream()
        .findFirst();
  }

  @Override
  public CustomerApplicationDetails updateStatus(CustomerApplicationStateUpdateCommand command) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("applicationReference", command.applicationReference())
            .addValue("applicationStatus", command.status().name())
            .addValue("statusReason", command.reason())
            .addValue("processedBy", command.actorSubject())
            .addValue("processedAt", Timestamp.from(command.processedAt()))
            .addValue("executionResult", toJson(command.executionResult()));

    return jdbcTemplate
        .query(
            """
            UPDATE customer_service_application
            SET application_status = :applicationStatus,
                status_reason = :statusReason,
                processed_by = :processedBy,
                processed_at = :processedAt,
                execution_result = CAST(:executionResult AS jsonb),
                updated_at = :processedAt
            WHERE application_reference = :applicationReference
            RETURNING application_reference,
                      user_id,
                      account_id,
                      application_type,
                      application_status,
                      mfa_verified,
                      mfa_verified_at,
                      payload,
                      submitted_at,
                      updated_at,
                      status_reason,
                      processed_by,
                      processed_at,
                      execution_result
            """,
            params,
            (rs, rowNum) -> mapDetails(rs))
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new CustomerApplicationNotFoundException("customer application was not found"));
  }

  private CustomerApplicationSubmission mapSubmission(ResultSet rs, int rowNum)
      throws SQLException {
    long accountId = rs.getLong("account_id");
    Long nullableAccountId = rs.wasNull() ? null : accountId;
    Timestamp mfaVerifiedAt = rs.getTimestamp("mfa_verified_at");
    return new CustomerApplicationSubmission(
        rs.getString("application_reference"),
        rs.getLong("user_id"),
        nullableAccountId,
        CustomerApplicationType.valueOf(rs.getString("application_type")),
        CustomerApplicationStatus.valueOf(rs.getString("application_status")),
        rs.getBoolean("mfa_verified"),
        mfaVerifiedAt == null ? null : mfaVerifiedAt.toInstant(),
        rs.getTimestamp("submitted_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant());
  }

  private CustomerApplicationDetails mapDetails(ResultSet rs) throws SQLException {
    long accountId = rs.getLong("account_id");
    Long nullableAccountId = rs.wasNull() ? null : accountId;
    Timestamp mfaVerifiedAt = rs.getTimestamp("mfa_verified_at");
    Timestamp processedAt = rs.getTimestamp("processed_at");
    return new CustomerApplicationDetails(
        rs.getString("application_reference"),
        rs.getLong("user_id"),
        nullableAccountId,
        CustomerApplicationType.valueOf(rs.getString("application_type")),
        CustomerApplicationStatus.valueOf(rs.getString("application_status")),
        rs.getBoolean("mfa_verified"),
        mfaVerifiedAt == null ? null : mfaVerifiedAt.toInstant(),
        fromJson(rs.getString("payload")),
        rs.getTimestamp("submitted_at").toInstant(),
        rs.getTimestamp("updated_at").toInstant(),
        rs.getString("status_reason"),
        rs.getString("processed_by"),
        processedAt == null ? null : processedAt.toInstant(),
        fromJson(rs.getString("execution_result")));
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("json serialization failed", ex);
    }
  }

  private Map<String, Object> fromJson(String value) {
    if (value == null || value.isBlank()) {
      return Map.of();
    }
    try {
      return objectMapper.readValue(value, MAP_TYPE);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("json deserialization failed", ex);
    }
  }
}
