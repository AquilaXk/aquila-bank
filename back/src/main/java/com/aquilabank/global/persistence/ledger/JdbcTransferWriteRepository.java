package com.aquilabank.global.persistence.ledger;

import com.aquilabank.domain.ledger.exception.CommandConflictException;
import com.aquilabank.domain.ledger.exception.CurrencyMismatchException;
import com.aquilabank.domain.ledger.exception.InsufficientBalanceException;
import com.aquilabank.domain.ledger.exception.SnapshotNotFoundException;
import com.aquilabank.domain.ledger.model.TransferCommand;
import com.aquilabank.domain.ledger.model.TransferResult;
import com.aquilabank.domain.ledger.port.TransferWritePort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 송금 쓰기 명령을 ledger, snapshot, read model, outbox 적재로 풀어내는 JDBC adapter */
@Repository
public class JdbcTransferWriteRepository implements TransferWritePort {

  private static final RowMapper<IdempotencyRecord> IDEMPOTENCY_ROW_MAPPER =
      (rs, rowNum) -> mapIdempotencyRecord(rs);

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public JdbcTransferWriteRepository(
      NamedParameterJdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public TransferResult transfer(TransferCommand command) {
    Instant now = Instant.now();
    String fingerprint = command.fingerprint();
    // 같은 idempotencyKey로 먼저 들어온 요청이 있으면 기존 처리 상태를 재사용합니다.
    boolean inserted = tryInsertIdempotency(command.idempotencyKey(), fingerprint, now);

    if (!inserted) {
      IdempotencyRecord record = loadIdempotencyForUpdate(command.idempotencyKey());
      if (!record.requestFingerprint().equals(fingerprint)) {
        throw new CommandConflictException("idempotencyKey is already used with another request");
      }
      if ("COMPLETED".equals(record.processingStatus()) && record.responsePayload() != null) {
        return readStoredResult(record.responsePayload());
      }
      if ("STARTED".equals(record.processingStatus()) && record.lockedUntil().isAfter(now)) {
        throw new CommandConflictException("same command is already in progress");
      }
      refreshIdempotencyLock(command.idempotencyKey(), now);
    }

    // source/target snapshot을 모두 잠가 송금 중간 상태가 다른 쓰기와 엇갈리지 않게 합니다.
    LockedBalanceSnapshot source = loadBalanceSnapshot(command.sourceAccountId());
    LockedBalanceSnapshot target = loadBalanceSnapshot(command.targetAccountId());

    if (!source.currencyCode().equals(command.currencyCode())
        || !target.currencyCode().equals(command.currencyCode())) {
      throw new CurrencyMismatchException("currency does not match source or target account");
    }
    if (source.availableBalanceMinor() < command.amountMinor()) {
      throw new InsufficientBalanceException("available balance is not enough");
    }

    Instant bookedAt = now;
    String transactionReference = "TRX-" + UUID.randomUUID();
    long sourceBalanceAfter = source.availableBalanceMinor() - command.amountMinor();
    long targetBalanceAfter = target.availableBalanceMinor() + command.amountMinor();

    // ledger entry를 먼저 남겨 원장 기록을 source of truth로 고정합니다.
    long debitEntryId =
        insertLedgerEntry(
            command.sourceAccountId(),
            transactionReference,
            "DEBIT",
            command.amountMinor(),
            command.currencyCode(),
            command.summary(),
            bookedAt);
    long creditEntryId =
        insertLedgerEntry(
            command.targetAccountId(),
            transactionReference,
            "CREDIT",
            command.amountMinor(),
            command.currencyCode(),
            command.summary(),
            bookedAt);

    updateBalanceSnapshot(command.sourceAccountId(), debitEntryId, sourceBalanceAfter, bookedAt);
    updateBalanceSnapshot(command.targetAccountId(), creditEntryId, targetBalanceAfter, bookedAt);

    // 조회 path는 read model을 별도로 적재해 대량 timeline lookup 비용을 낮춥니다.
    insertTransactionReadModel(
        debitEntryId,
        command.sourceAccountId(),
        transactionReference,
        "DEBIT",
        command.amountMinor(),
        sourceBalanceAfter,
        command.currencyCode(),
        command.summary(),
        "ACCOUNT-" + command.targetAccountId(),
        bookedAt);
    insertTransactionReadModel(
        creditEntryId,
        command.targetAccountId(),
        transactionReference,
        "CREDIT",
        command.amountMinor(),
        targetBalanceAfter,
        command.currencyCode(),
        command.summary(),
        "ACCOUNT-" + command.sourceAccountId(),
        bookedAt);

    // 비동기 알림은 같은 transaction 안에서 outbox에 적재하고 실제 delivery는 나중에 분리합니다.
    insertOutboxEvent(command, transactionReference, bookedAt);

    TransferResult result =
        new TransferResult(
            transactionReference,
            command.sourceAccountId(),
            command.targetAccountId(),
            command.amountMinor(),
            command.currencyCode(),
            sourceBalanceAfter,
            bookedAt,
            "BOOKED");
    // 최종 응답을 저장해 동일 요청 재시도 시 같은 결과를 재사용합니다.
    completeIdempotency(command.idempotencyKey(), result, bookedAt);
    return result;
  }

  private boolean tryInsertIdempotency(String key, String fingerprint, Instant now) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("idempotencyKey", key)
            .addValue("fingerprint", fingerprint)
            .addValue("now", Timestamp.from(now))
            .addValue("lockedUntil", Timestamp.from(now.plusSeconds(30)));

    return jdbcTemplate
        .query(
            """
            INSERT INTO command_idempotency (
                idempotency_key,
                request_fingerprint,
                processing_status,
                locked_until,
                created_at,
                updated_at
            )
            VALUES (
                :idempotencyKey,
                :fingerprint,
                'STARTED',
                :lockedUntil,
                :now,
                :now
            )
            ON CONFLICT DO NOTHING
            RETURNING 1
            """,
            params,
            (rs, rowNum) -> rs.getInt(1))
        .stream()
        .findFirst()
        .isPresent();
  }

  private IdempotencyRecord loadIdempotencyForUpdate(String key) {
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("idempotencyKey", key);
    return jdbcTemplate
        .query(
            """
            SELECT idempotency_key,
                   request_fingerprint,
                   processing_status,
                   response_payload::text AS response_payload,
                   locked_until
            FROM command_idempotency
            WHERE idempotency_key = :idempotencyKey
            FOR UPDATE
            """,
            params,
            IDEMPOTENCY_ROW_MAPPER)
        .stream()
        .findFirst()
        .orElseThrow(() -> new CommandConflictException("idempotencyKey state is missing"));
  }

  private void refreshIdempotencyLock(String key, Instant now) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("idempotencyKey", key)
            .addValue("now", Timestamp.from(now))
            .addValue("lockedUntil", Timestamp.from(now.plusSeconds(30)));
    jdbcTemplate.update(
        """
        UPDATE command_idempotency
        SET processing_status = 'STARTED',
            response_code = NULL,
            response_payload = NULL,
            locked_until = :lockedUntil,
            updated_at = :now
        WHERE idempotency_key = :idempotencyKey
        """,
        params);
  }

  private LockedBalanceSnapshot loadBalanceSnapshot(long accountId) {
    MapSqlParameterSource params = new MapSqlParameterSource().addValue("accountId", accountId);
    return jdbcTemplate
        .query(
            """
            SELECT account_id,
                   last_applied_ledger_entry_id,
                   available_balance_minor,
                   pending_balance_minor,
                   currency_code
            FROM account_balance_snapshot
            WHERE account_id = :accountId
            FOR UPDATE
            """,
            params,
            (rs, rowNum) ->
                new LockedBalanceSnapshot(
                    rs.getLong("account_id"),
                    rs.getLong("last_applied_ledger_entry_id"),
                    rs.getLong("available_balance_minor"),
                    rs.getLong("pending_balance_minor"),
                    rs.getString("currency_code")))
        .stream()
        .findFirst()
        .orElseThrow(
            () -> new SnapshotNotFoundException("account snapshot is missing: " + accountId));
  }

  private long insertLedgerEntry(
      long accountId,
      String transactionReference,
      String direction,
      long amountMinor,
      String currencyCode,
      String summary,
      Instant bookedAt) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("transactionReference", transactionReference)
            .addValue("entryReference", "ENT-" + UUID.randomUUID())
            .addValue("direction", direction)
            .addValue("amountMinor", amountMinor)
            .addValue("currencyCode", currencyCode)
            .addValue("summary", summary)
            .addValue("bookedAt", Timestamp.from(bookedAt));

    Long id =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO ledger_entry (
                account_id,
                transaction_reference,
                entry_reference,
                direction,
                entry_status,
                amount_minor,
                currency_code,
                booked_at,
                description,
                occurred_at,
                created_at,
                updated_at
            )
            VALUES (
                :accountId,
                :transactionReference,
                :entryReference,
                :direction,
                'BOOKED',
                :amountMinor,
                :currencyCode,
                :bookedAt,
                :summary,
                :bookedAt,
                :bookedAt,
                :bookedAt
            )
            RETURNING id
            """,
            params,
            Long.class);
    if (id == null) {
      throw new IllegalStateException("ledger entry insert did not return id");
    }
    return id;
  }

  private void updateBalanceSnapshot(
      long accountId, long ledgerEntryId, long availableBalanceAfter, Instant updatedAt) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("ledgerEntryId", ledgerEntryId)
            .addValue("availableBalanceAfter", availableBalanceAfter)
            .addValue("updatedAt", Timestamp.from(updatedAt));

    int updated =
        jdbcTemplate.update(
            """
            UPDATE account_balance_snapshot
            SET last_applied_ledger_entry_id = :ledgerEntryId,
                available_balance_minor = :availableBalanceAfter,
                updated_at = :updatedAt
            WHERE account_id = :accountId
            """,
            params);
    if (updated != 1) {
      throw new IllegalStateException("balance snapshot update failed");
    }
  }

  private void insertTransactionReadModel(
      long ledgerEntryId,
      long accountId,
      String transactionReference,
      String direction,
      long amountMinor,
      long balanceAfterMinor,
      String currencyCode,
      String summary,
      String counterpartyMaskedName,
      Instant bookedAt) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("ledgerEntryId", ledgerEntryId)
            .addValue("accountId", accountId)
            .addValue("transactionReference", transactionReference)
            .addValue("direction", direction)
            .addValue("amountMinor", amountMinor)
            .addValue("balanceAfterMinor", balanceAfterMinor)
            .addValue("currencyCode", currencyCode)
            .addValue("summary", summary)
            .addValue("counterpartyMaskedName", counterpartyMaskedName)
            .addValue("bookedAt", Timestamp.from(bookedAt));

    jdbcTemplate.update(
        """
        INSERT INTO transaction_read_model (
            ledger_entry_id,
            account_id,
            transaction_reference,
            direction,
            transaction_status,
            amount_minor,
            balance_after_minor,
            currency_code,
            summary,
            counterparty_masked_name,
            booked_at,
            created_at
        )
        VALUES (
            :ledgerEntryId,
            :accountId,
            :transactionReference,
            :direction,
            'BOOKED',
            :amountMinor,
            :balanceAfterMinor,
            :currencyCode,
            :summary,
            :counterpartyMaskedName,
            :bookedAt,
            :bookedAt
        )
        """,
        params);
  }

  private void insertOutboxEvent(
      TransferCommand command, String transactionReference, Instant bookedAt) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("aggregateId", transactionReference)
            .addValue("eventKey", "transfer-booked:" + transactionReference)
            .addValue("payload", toJson(outboxPayload(command, transactionReference, bookedAt)))
            .addValue("bookedAt", Timestamp.from(bookedAt));

    jdbcTemplate.update(
        """
        INSERT INTO outbox_event (
            aggregate_type,
            aggregate_id,
            event_type,
            event_key,
            payload,
            publish_status,
            available_at,
            created_at,
            updated_at
        )
        VALUES (
            'TRANSFER',
            :aggregateId,
            'TransferBooked',
            :eventKey,
            CAST(:payload AS jsonb),
            'PENDING',
            :bookedAt,
            :bookedAt,
            :bookedAt
        )
        """,
        params);
  }

  private void completeIdempotency(String key, TransferResult result, Instant completedAt) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("idempotencyKey", key)
            .addValue("completedAt", Timestamp.from(completedAt))
            .addValue("responsePayload", toJson(result))
            .addValue("responseCode", 200);

    jdbcTemplate.update(
        """
        UPDATE command_idempotency
        SET processing_status = 'COMPLETED',
            response_code = :responseCode,
            response_payload = CAST(:responsePayload AS jsonb),
            locked_until = :completedAt,
            updated_at = :completedAt
        WHERE idempotency_key = :idempotencyKey
        """,
        params);
  }

  private TransferResult readStoredResult(String rawPayload) {
    try {
      // 완료된 idempotency 요청은 저장된 payload를 그대로 복원해 재응답합니다.
      return objectMapper.readValue(rawPayload, TransferResult.class);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("stored idempotency payload is invalid", ex);
    }
  }

  private String toJson(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("json serialization failed", ex);
    }
  }

  private Map<String, Object> outboxPayload(
      TransferCommand command, String transactionReference, Instant bookedAt) {
    // delivery channel이 바뀌어도 공통 event payload shape은 여기서 고정합니다.
    return Map.of(
        "transactionReference",
        transactionReference,
        "sourceAccountId",
        command.sourceAccountId(),
        "targetAccountId",
        command.targetAccountId(),
        "amountMinor",
        command.amountMinor(),
        "currencyCode",
        command.currencyCode(),
        "summary",
        command.summary(),
        "bookedAt",
        bookedAt.toString());
  }

  private static IdempotencyRecord mapIdempotencyRecord(ResultSet rs) throws SQLException {
    OffsetDateTime lockedUntil = rs.getObject("locked_until", OffsetDateTime.class);
    return new IdempotencyRecord(
        rs.getString("idempotency_key"),
        rs.getString("request_fingerprint"),
        rs.getString("processing_status"),
        rs.getString("response_payload"),
        lockedUntil.toInstant());
  }

  private record IdempotencyRecord(
      String idempotencyKey,
      String requestFingerprint,
      String processingStatus,
      String responsePayload,
      Instant lockedUntil) {}

  private record LockedBalanceSnapshot(
      long accountId,
      long lastAppliedLedgerEntryId,
      long availableBalanceMinor,
      long pendingBalanceMinor,
      String currencyCode) {}
}
