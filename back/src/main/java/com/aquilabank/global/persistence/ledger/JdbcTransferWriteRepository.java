package com.aquilabank.global.persistence.ledger;

import com.aquilabank.domain.ledger.exception.CommandConflictException;
import com.aquilabank.domain.ledger.exception.CurrencyMismatchException;
import com.aquilabank.domain.ledger.exception.InsufficientBalanceException;
import com.aquilabank.domain.ledger.exception.SnapshotNotFoundException;
import com.aquilabank.domain.ledger.exception.TransferAccountStatusBlockedException;
import com.aquilabank.domain.ledger.exception.TransferReversalConflictException;
import com.aquilabank.domain.ledger.exception.TransferReversalNotFoundException;
import com.aquilabank.domain.ledger.model.TransferCommand;
import com.aquilabank.domain.ledger.model.TransferResult;
import com.aquilabank.domain.ledger.model.TransferReversalCommand;
import com.aquilabank.domain.ledger.model.TransferReversalResult;
import com.aquilabank.domain.ledger.port.TransferReversalWritePort;
import com.aquilabank.domain.ledger.port.TransferWritePort;
import com.aquilabank.global.web.RequestTraceContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 송금 쓰기 명령을 ledger, snapshot, read model, outbox 적재로 풀어내는 JDBC adapter */
@Repository
public class JdbcTransferWriteRepository implements TransferWritePort, TransferReversalWritePort {

  private static final String ACTIVE_ACCOUNT_STATUS = "ACTIVE";
  private static final String PERSIST_ACCOUNTING_BUNDLE_SQL =
      """
      -- 송금/역분개 회계 적재 7건(원장 2, 잔액 스냅샷 2, read model 2, outbox 1)을 단일 CTE 왕복으로 처리합니다.
      WITH inserted_ledger AS (
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
              trace_id,
              metadata,
              occurred_at,
              created_at,
              updated_at
          )
          VALUES
              (
                  :firstAccountId,
                  :transactionReference,
                  :firstEntryReference,
                  :firstDirection,
                  'BOOKED',
                  :amountMinor,
                  :currencyCode,
                  :bookedAt,
                  :summary,
                  :traceId,
                  CAST(:metadata AS jsonb),
                  :bookedAt,
                  :bookedAt,
                  :bookedAt
              ),
              (
                  :secondAccountId,
                  :transactionReference,
                  :secondEntryReference,
                  :secondDirection,
                  'BOOKED',
                  :amountMinor,
                  :currencyCode,
                  :bookedAt,
                  :summary,
                  :traceId,
                  CAST(:metadata AS jsonb),
                  :bookedAt,
                  :bookedAt,
                  :bookedAt
              )
          RETURNING id, account_id, direction
      ),
      updated_snapshots AS (
          UPDATE account_balance_snapshot s
          SET last_applied_ledger_entry_id = l.id,
              available_balance_minor = CASE
                  WHEN s.account_id = :firstAccountId THEN :firstBalanceAfter
                  ELSE :secondBalanceAfter
              END,
              updated_at = :bookedAt
          FROM inserted_ledger l
          WHERE s.account_id = l.account_id
          RETURNING s.account_id
      ),
      inserted_read_model AS (
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
          SELECT l.id,
                 l.account_id,
                 :transactionReference,
                 l.direction,
                 'BOOKED',
                 :amountMinor,
                 CASE
                     WHEN l.account_id = :firstAccountId THEN :firstBalanceAfter
                     ELSE :secondBalanceAfter
                 END,
                 :currencyCode,
                 :summary,
                 CASE
                     WHEN l.account_id = :firstAccountId THEN :firstCounterparty
                     ELSE :secondCounterparty
                 END,
                 :bookedAt,
                 :bookedAt
          FROM inserted_ledger l
      ),
      inserted_outbox AS (
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
              :transactionReference,
              :outboxEventType,
              :outboxEventKey,
              CAST(:outboxPayload AS jsonb),
              'PENDING',
              :bookedAt,
              :bookedAt,
              :bookedAt
          )
      )
      SELECT COUNT(*) FROM updated_snapshots
      """;

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
    return transfer(command, null);
  }

  @Override
  @Transactional
  public TransferResult transfer(
      TransferCommand command, Consumer<TransferCommand> limitValidator) {
    Instant now = Instant.now();
    String fingerprint = command.fingerprint();
    // 같은 idempotencyKey로 이미 처리된 요청은 한도 소진 이후에도 저장된 결과를 먼저 재사용합니다.
    Optional<IdempotencyRecord> existingIdempotency =
        findIdempotencyForUpdate(command.idempotencyKey());
    if (existingIdempotency.isPresent()) {
      IdempotencyRecord record = existingIdempotency.get();
      if (!record.requestFingerprint().equals(fingerprint)) {
        throw new CommandConflictException("idempotencyKey is already used with another request");
      }
      if ("COMPLETED".equals(record.processingStatus()) && record.responsePayload() != null) {
        return readStoredResult(record.responsePayload(), TransferResult.class);
      }
      if ("STARTED".equals(record.processingStatus()) && record.lockedUntil().isAfter(now)) {
        throw new CommandConflictException("same command is already in progress");
      }
      refreshIdempotencyLock(command.idempotencyKey(), now);
    } else {
      boolean inserted = tryInsertIdempotency(command.idempotencyKey(), fingerprint, now);
      if (!inserted) {
        IdempotencyRecord record = loadIdempotencyForUpdate(command.idempotencyKey());
        if (!record.requestFingerprint().equals(fingerprint)) {
          throw new CommandConflictException("idempotencyKey is already used with another request");
        }
        if ("COMPLETED".equals(record.processingStatus()) && record.responsePayload() != null) {
          return readStoredResult(record.responsePayload(), TransferResult.class);
        }
        if ("STARTED".equals(record.processingStatus()) && record.lockedUntil().isAfter(now)) {
          throw new CommandConflictException("same command is already in progress");
        }
        refreshIdempotencyLock(command.idempotencyKey(), now);
      }
    }

    // 교차 이체 시 교착상태(Deadlock)를 방지하기 위해 두 계좌를 ID 오름차순 단일 쿼리로 잠급니다.
    long firstLockId = Math.min(command.sourceAccountId(), command.targetAccountId());
    long secondLockId = Math.max(command.sourceAccountId(), command.targetAccountId());
    LockedBalanceSnapshotPair lockedPair = loadBalanceSnapshots(firstLockId, secondLockId);
    LockedBalanceSnapshot source =
        command.sourceAccountId() == firstLockId ? lockedPair.first() : lockedPair.second();
    LockedBalanceSnapshot target =
        command.targetAccountId() == firstLockId ? lockedPair.first() : lockedPair.second();
    verifyTransferableAccount(source);
    verifyTransferableAccount(target);

    if (!source.currencyCode().equals(command.currencyCode())
        || !target.currencyCode().equals(command.currencyCode())) {
      throw new CurrencyMismatchException("currency does not match source or target account");
    }
    if (source.availableBalanceMinor() < command.amountMinor()) {
      throw new InsufficientBalanceException("available balance is not enough");
    }
    // 출금 계좌 락을 획득한 상태에서 일일 한도를 다시 검증해 동시 요청의 TOCTOU 우회를 차단합니다.
    if (limitValidator != null) {
      limitValidator.accept(command);
    }

    Instant bookedAt = now;
    String transactionReference = "TRX-" + UUID.randomUUID();
    long sourceBalanceAfter = source.availableBalanceMinor() - command.amountMinor();
    long targetBalanceAfter = Math.addExact(target.availableBalanceMinor(), command.amountMinor());

    // ledger entry, 잔액 스냅샷, read model, outbox 적재를 단일 CTE 왕복으로 고정합니다.
    persistAccountingBundle(
        new AccountingBundleCommand(
            command.sourceAccountId(),
            "DEBIT",
            sourceBalanceAfter,
            "ACCOUNT-" + command.targetAccountId(),
            command.targetAccountId(),
            "CREDIT",
            targetBalanceAfter,
            "ACCOUNT-" + command.sourceAccountId(),
            transactionReference,
            command.amountMinor(),
            command.currencyCode(),
            command.summary(),
            "{}",
            bookedAt,
            "TransferBooked",
            "transfer-booked:" + transactionReference,
            toJson(outboxPayload(command, transactionReference, bookedAt))));

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

  @Override
  @Transactional
  public TransferReversalResult reverse(TransferReversalCommand command) {
    Instant now = Instant.now();
    boolean inserted = tryInsertIdempotency(command.idempotencyKey(), command.fingerprint(), now);

    if (!inserted) {
      IdempotencyRecord record = loadIdempotencyForUpdate(command.idempotencyKey());
      if (!record.requestFingerprint().equals(command.fingerprint())) {
        throw new CommandConflictException("idempotencyKey is already used with another request");
      }
      if ("COMPLETED".equals(record.processingStatus()) && record.responsePayload() != null) {
        return readStoredResult(record.responsePayload(), TransferReversalResult.class);
      }
      if ("STARTED".equals(record.processingStatus()) && record.lockedUntil().isAfter(now)) {
        throw new CommandConflictException("same command is already in progress");
      }
      refreshIdempotencyLock(command.idempotencyKey(), now);
    }

    OriginalTransferRecord original =
        loadOriginalTransferForUpdate(
            command.originalTransactionReference(), command.sourceAccountId());
    long alreadyReversedAmount =
        loadExistingReversalAmountForUpdate(original.transactionReference());
    long remainingAmount = original.amountMinor() - alreadyReversedAmount;
    if (remainingAmount <= 0) {
      throw new CommandConflictException("transfer is already reversed");
    }
    long reversalAmount = command.amountMinor() == null ? remainingAmount : command.amountMinor();
    if (reversalAmount > remainingAmount) {
      throw new CommandConflictException("reversal amount exceeds remaining amount");
    }

    // 교차 환불 및 동시 이체 시 교착상태(Deadlock)를 방지하기 위해 두 계좌를 ID 오름차순 단일 쿼리로 잠급니다.
    long firstLockId = Math.min(original.sourceAccountId(), original.targetAccountId());
    long secondLockId = Math.max(original.sourceAccountId(), original.targetAccountId());
    LockedBalanceSnapshotPair lockedPair = loadBalanceSnapshots(firstLockId, secondLockId);
    LockedBalanceSnapshot source =
        original.sourceAccountId() == firstLockId ? lockedPair.first() : lockedPair.second();
    LockedBalanceSnapshot target =
        original.targetAccountId() == firstLockId ? lockedPair.first() : lockedPair.second();
    verifyTransferableAccount(source);
    verifyTransferableAccount(target);
    if (!source.currencyCode().equals(original.currencyCode())
        || !target.currencyCode().equals(original.currencyCode())) {
      throw new CurrencyMismatchException("currency does not match original transfer");
    }
    if (target.availableBalanceMinor() < reversalAmount) {
      throw new InsufficientBalanceException("reversal target balance is not enough");
    }

    Instant bookedAt = now;
    String reversalTransactionReference = "TRX-" + UUID.randomUUID();
    long sourceBalanceAfter = Math.addExact(source.availableBalanceMinor(), reversalAmount);
    long targetBalanceAfter = target.availableBalanceMinor() - reversalAmount;
    long totalReversedAmount = Math.addExact(alreadyReversedAmount, reversalAmount);
    String originalStatus =
        totalReversedAmount == original.amountMinor() ? "REVERSED" : "PARTIALLY_REVERSED";
    String reversalMetadata =
        toJson(
            Map.of(
                "originalTransactionReference", original.transactionReference(),
                "reversalAmountMinor", reversalAmount,
                "reversalReason", command.reversalReason().name()));

    persistAccountingBundle(
        new AccountingBundleCommand(
            original.sourceAccountId(),
            "CREDIT",
            sourceBalanceAfter,
            "ACCOUNT-" + original.targetAccountId(),
            original.targetAccountId(),
            "DEBIT",
            targetBalanceAfter,
            "ACCOUNT-" + original.sourceAccountId(),
            reversalTransactionReference,
            reversalAmount,
            original.currencyCode(),
            command.summary(),
            reversalMetadata,
            bookedAt,
            "TransferReversed",
            "transfer-reversed:" + reversalTransactionReference,
            toJson(
                Map.of(
                    "originalTransactionReference", original.transactionReference(),
                    "reversalTransactionReference", reversalTransactionReference,
                    "sourceAccountId", original.sourceAccountId(),
                    "targetAccountId", original.targetAccountId(),
                    "amountMinor", reversalAmount,
                    "currencyCode", original.currencyCode(),
                    "reversalReason", command.reversalReason().name(),
                    "bookedAt", bookedAt.toString()))));

    markTransactionReadModelStatus(original, originalStatus);
    insertTransferReversal(
        original, reversalTransactionReference, reversalAmount, command, bookedAt);

    TransferReversalResult result =
        new TransferReversalResult(
            original.transactionReference(),
            reversalTransactionReference,
            original.sourceAccountId(),
            original.targetAccountId(),
            reversalAmount,
            original.currencyCode(),
            sourceBalanceAfter,
            bookedAt,
            originalStatus);
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

  private Optional<IdempotencyRecord> findIdempotencyForUpdate(String key) {
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
        .findFirst();
  }

  private IdempotencyRecord loadIdempotencyForUpdate(String key) {
    return findIdempotencyForUpdate(key)
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

  private LockedBalanceSnapshotPair loadBalanceSnapshots(
      long firstAccountId, long secondAccountId) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("firstAccountId", firstAccountId)
            .addValue("secondAccountId", secondAccountId);
    List<LockedBalanceSnapshot> snapshots =
        jdbcTemplate.query(
            """
            SELECT s.account_id,
                   s.last_applied_ledger_entry_id,
                   s.available_balance_minor,
                   s.pending_balance_minor,
                   s.currency_code,
                   a.account_status
            FROM account_balance_snapshot s
            JOIN bank_account a ON a.id = s.account_id
            WHERE s.account_id IN (:firstAccountId, :secondAccountId)
            ORDER BY s.account_id ASC
            FOR UPDATE OF s, a
            """,
            params,
            (rs, rowNum) ->
                new LockedBalanceSnapshot(
                    rs.getLong("account_id"),
                    rs.getLong("last_applied_ledger_entry_id"),
                    rs.getLong("available_balance_minor"),
                    rs.getLong("pending_balance_minor"),
                    rs.getString("currency_code"),
                    rs.getString("account_status")));
    LockedBalanceSnapshot first = null;
    LockedBalanceSnapshot second = null;
    for (LockedBalanceSnapshot snapshot : snapshots) {
      if (snapshot.accountId() == firstAccountId) {
        first = snapshot;
      }
      if (snapshot.accountId() == secondAccountId) {
        second = snapshot;
      }
    }
    if (first == null) {
      throw new SnapshotNotFoundException("account snapshot is missing: " + firstAccountId);
    }
    if (second == null) {
      throw new SnapshotNotFoundException("account snapshot is missing: " + secondAccountId);
    }
    return new LockedBalanceSnapshotPair(first, second);
  }

  private void verifyTransferableAccount(LockedBalanceSnapshot snapshot) {
    if (!ACTIVE_ACCOUNT_STATUS.equals(snapshot.accountStatus())) {
      throw new TransferAccountStatusBlockedException("account access is denied");
    }
  }

  private void persistAccountingBundle(AccountingBundleCommand bundle) {
    String traceId = RequestTraceContext.currentRequestId().orElse(null);
    Timestamp bookedAt = Timestamp.from(bundle.bookedAt());
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("firstAccountId", bundle.firstAccountId())
            .addValue("firstEntryReference", "ENT-" + UUID.randomUUID())
            .addValue("firstDirection", bundle.firstDirection())
            .addValue("firstBalanceAfter", bundle.firstBalanceAfter())
            .addValue("firstCounterparty", bundle.firstCounterparty())
            .addValue("secondAccountId", bundle.secondAccountId())
            .addValue("secondEntryReference", "ENT-" + UUID.randomUUID())
            .addValue("secondDirection", bundle.secondDirection())
            .addValue("secondBalanceAfter", bundle.secondBalanceAfter())
            .addValue("secondCounterparty", bundle.secondCounterparty())
            .addValue("transactionReference", bundle.transactionReference())
            .addValue("amountMinor", bundle.amountMinor())
            .addValue("currencyCode", bundle.currencyCode())
            .addValue("summary", bundle.summary())
            .addValue("traceId", traceId)
            .addValue("metadata", bundle.metadataJson())
            .addValue("bookedAt", bookedAt)
            .addValue("outboxEventType", bundle.outboxEventType())
            .addValue("outboxEventKey", bundle.outboxEventKey())
            .addValue("outboxPayload", bundle.outboxPayload());

    Integer updatedSnapshots =
        jdbcTemplate.queryForObject(PERSIST_ACCOUNTING_BUNDLE_SQL, params, Integer.class);
    if (updatedSnapshots == null || updatedSnapshots != 2) {
      throw new IllegalStateException("balance snapshot update failed");
    }
  }

  private void insertTransferReversal(
      OriginalTransferRecord original,
      String reversalTransactionReference,
      long reversalAmount,
      TransferReversalCommand command,
      Instant createdAt) {
    try {
      jdbcTemplate.update(
          """
          INSERT INTO transfer_reversal (
              original_transaction_reference,
              reversal_transaction_reference,
              source_account_id,
              target_account_id,
              amount_minor,
              currency_code,
              reversal_reason,
              summary,
              created_at
          )
          VALUES (
              :originalTransactionReference,
              :reversalTransactionReference,
              :sourceAccountId,
              :targetAccountId,
              :amountMinor,
              :currencyCode,
              :reversalReason,
              :summary,
              :createdAt
          )
          """,
          new MapSqlParameterSource()
              .addValue("originalTransactionReference", original.transactionReference())
              .addValue("reversalTransactionReference", reversalTransactionReference)
              .addValue("sourceAccountId", original.sourceAccountId())
              .addValue("targetAccountId", original.targetAccountId())
              .addValue("amountMinor", reversalAmount)
              .addValue("currencyCode", original.currencyCode())
              .addValue("reversalReason", command.reversalReason().name())
              .addValue("summary", command.summary())
              .addValue("createdAt", Timestamp.from(createdAt)));
    } catch (DuplicateKeyException ex) {
      throw new CommandConflictException("transfer is already reversed");
    }
  }

  private long loadExistingReversalAmountForUpdate(String originalTransactionReference) {
    return jdbcTemplate
        .query(
            """
            SELECT amount_minor
            FROM transfer_reversal
            WHERE original_transaction_reference = :originalTransactionReference
            FOR UPDATE
            """,
            new MapSqlParameterSource()
                .addValue("originalTransactionReference", originalTransactionReference),
            (rs, rowNum) -> rs.getLong("amount_minor"))
        .stream()
        .mapToLong(Long::longValue)
        .sum();
  }

  private OriginalTransferRecord loadOriginalTransferForUpdate(
      String originalTransactionReference, long sourceAccountId) {
    List<OriginalTransferLedgerEntry> entries =
        jdbcTemplate.query(
            """
            SELECT account_id,
                   transaction_reference,
                   direction,
                   amount_minor,
                   currency_code,
                   description,
                   booked_at
            FROM ledger_entry
            WHERE transaction_reference = :transactionReference
            ORDER BY id
            FOR UPDATE
            """,
            new MapSqlParameterSource()
                .addValue("transactionReference", originalTransactionReference),
            (rs, rowNum) ->
                new OriginalTransferLedgerEntry(
                    rs.getLong("account_id"),
                    rs.getString("transaction_reference"),
                    rs.getString("direction"),
                    rs.getLong("amount_minor"),
                    rs.getString("currency_code"),
                    rs.getString("description"),
                    rs.getTimestamp("booked_at").toInstant()));
    if (entries.size() != 2) {
      throw new TransferReversalNotFoundException("transfer reversal target is not found");
    }

    OriginalTransferLedgerEntry debit = null;
    OriginalTransferLedgerEntry credit = null;
    for (OriginalTransferLedgerEntry entry : entries) {
      if ("DEBIT".equals(entry.direction())) {
        debit = entry;
      } else if ("CREDIT".equals(entry.direction())) {
        credit = entry;
      }
    }
    if (debit == null || credit == null) {
      throw new TransferReversalNotFoundException("transfer reversal target is not found");
    }
    if (debit.accountId() != sourceAccountId) {
      throw new TransferReversalNotFoundException("transfer reversal target is not found");
    }
    if (isReversalTransaction(originalTransactionReference)) {
      throw new TransferReversalConflictException("reversal transaction cannot be reversed");
    }
    if (debit.amountMinor() != credit.amountMinor()
        || !debit.currencyCode().equals(credit.currencyCode())) {
      throw new IllegalStateException("original transfer ledger rows are inconsistent");
    }
    return new OriginalTransferRecord(
        debit.transactionReference(),
        debit.accountId(),
        credit.accountId(),
        debit.amountMinor(),
        debit.currencyCode(),
        debit.bookedAt());
  }

  private boolean isReversalTransaction(String transactionReference) {
    Boolean exists =
        jdbcTemplate.queryForObject(
            """
            SELECT EXISTS (
                SELECT 1
                FROM transfer_reversal
                WHERE reversal_transaction_reference = :transactionReference
            )
            """,
            new MapSqlParameterSource().addValue("transactionReference", transactionReference),
            Boolean.class);
    return Boolean.TRUE.equals(exists);
  }

  private void markTransactionReadModelStatus(
      OriginalTransferRecord original, String transactionStatus) {
    // 파티션 프루닝을 위해 booked_at과 account_id를 함께 지정하고, 아카이브된 원거래도 갱신합니다.
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("transactionReference", original.transactionReference())
            .addValue("bookedAt", Timestamp.from(original.bookedAt()))
            .addValue("sourceAccountId", original.sourceAccountId())
            .addValue("targetAccountId", original.targetAccountId())
            .addValue("transactionStatus", transactionStatus);
    int updated =
        jdbcTemplate.update(
            """
            UPDATE transaction_read_model
            SET transaction_status = :transactionStatus
            WHERE transaction_reference = :transactionReference
              AND booked_at = :bookedAt
              AND account_id IN (:sourceAccountId, :targetAccountId)
            """,
            params);
    if (updated < 2) {
      int archiveUpdated =
          jdbcTemplate.update(
              """
              UPDATE transaction_read_model_archive
              SET transaction_status = :transactionStatus
              WHERE transaction_reference = :transactionReference
                AND booked_at = :bookedAt
                AND account_id IN (:sourceAccountId, :targetAccountId)
              """,
              params);
      updated += archiveUpdated;
    }
    if (updated != 2) {
      throw new IllegalStateException("original transaction read model update failed");
    }
  }

  private void completeIdempotency(String key, Object result, Instant completedAt) {
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

  private <T> T readStoredResult(String rawPayload, Class<T> type) {
    try {
      // 완료된 idempotency 요청은 저장된 payload를 그대로 복원해 재응답합니다.
      return objectMapper.readValue(rawPayload, type);
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

  private record OriginalTransferLedgerEntry(
      long accountId,
      String transactionReference,
      String direction,
      long amountMinor,
      String currencyCode,
      String summary,
      Instant bookedAt) {}

  private record OriginalTransferRecord(
      String transactionReference,
      long sourceAccountId,
      long targetAccountId,
      long amountMinor,
      String currencyCode,
      Instant bookedAt) {}

  private record LockedBalanceSnapshot(
      long accountId,
      long lastAppliedLedgerEntryId,
      long availableBalanceMinor,
      long pendingBalanceMinor,
      String currencyCode,
      String accountStatus) {}

  private record LockedBalanceSnapshotPair(
      LockedBalanceSnapshot first, LockedBalanceSnapshot second) {}

  private record AccountingBundleCommand(
      long firstAccountId,
      String firstDirection,
      long firstBalanceAfter,
      String firstCounterparty,
      long secondAccountId,
      String secondDirection,
      long secondBalanceAfter,
      String secondCounterparty,
      String transactionReference,
      long amountMinor,
      String currencyCode,
      String summary,
      String metadataJson,
      Instant bookedAt,
      String outboxEventType,
      String outboxEventKey,
      String outboxPayload) {}
}
