package com.aquilabank.global.persistence.account;

import com.aquilabank.domain.account.model.AccountBootstrapCommand;
import com.aquilabank.domain.account.model.AccountBootstrapResult;
import com.aquilabank.domain.account.port.AccountBootstrapPort;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** bank_account, opening ledger, snapshot을 함께 만드는 JDBC bootstrap adapter */
@Repository
public class JdbcAccountBootstrapRepository implements AccountBootstrapPort {

  private static final String ACTIVE = "ACTIVE";
  private static final String BOOKED = "BOOKED";
  private static final String CREDIT = "CREDIT";
  private static final String OPENING_SUMMARY = "initial funding";

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcAccountBootstrapRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional
  public AccountBootstrapResult bootstrap(AccountBootstrapCommand command) {
    Instant now = Instant.now();
    String accountNumber = nextAccountNumber();
    long accountId = insertBankAccount(accountNumber, command, now);

    long lastAppliedLedgerEntryId = 0L;
    // initial balance가 있으면 opening credit를 같이 남겨 snapshot과 ledger source of truth를 맞춥니다.
    if (command.initialBalanceMinor() > 0) {
      lastAppliedLedgerEntryId = insertOpeningLedgerEntry(accountId, accountNumber, command, now);
      insertOpeningReadModel(lastAppliedLedgerEntryId, accountId, accountNumber, command, now);
    }

    insertBalanceSnapshot(accountId, lastAppliedLedgerEntryId, command, now);

    return new AccountBootstrapResult(
        accountId,
        accountNumber,
        command.displayName(),
        command.currencyCode(),
        command.initialBalanceMinor(),
        ACTIVE,
        now);
  }

  private String nextAccountNumber() {
    // account_number는 UUID보다 운영 추적이 쉬운 숫자 문자열을 sequence로 발급합니다.
    return jdbcTemplate.queryForObject(
        """
        SELECT '100' || LPAD(nextval('bank_account_number_seq')::text, 11, '0')
        """,
        new MapSqlParameterSource(),
        String.class);
  }

  private long insertBankAccount(
      String accountNumber, AccountBootstrapCommand command, Instant now) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("accountNumber", accountNumber)
            .addValue("displayName", command.displayName())
            .addValue("currencyCode", command.currencyCode())
            .addValue("accountStatus", ACTIVE)
            .addValue("now", Timestamp.from(now));

    Long accountId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO bank_account (
                account_number,
                display_name,
                account_status,
                currency_code,
                created_at,
                updated_at
            )
            VALUES (
                :accountNumber,
                :displayName,
                :accountStatus,
                :currencyCode,
                :now,
                :now
            )
            RETURNING id
            """,
            params,
            Long.class);

    if (accountId == null) {
      throw new IllegalStateException("bank_account insert did not return an id");
    }
    return accountId;
  }

  private long insertOpeningLedgerEntry(
      long accountId, String accountNumber, AccountBootstrapCommand command, Instant bookedAt) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("transactionReference", "OPEN-" + accountNumber)
            .addValue("entryReference", "OPEN-ENTRY-" + UUID.randomUUID())
            .addValue("direction", CREDIT)
            .addValue("entryStatus", BOOKED)
            .addValue("amountMinor", command.initialBalanceMinor())
            .addValue("currencyCode", command.currencyCode())
            .addValue("description", OPENING_SUMMARY)
            .addValue("bookedAt", Timestamp.from(bookedAt));

    Long entryId =
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
                metadata,
                created_at,
                updated_at
            )
            VALUES (
                :accountId,
                :transactionReference,
                :entryReference,
                :direction,
                :entryStatus,
                :amountMinor,
                :currencyCode,
                :bookedAt,
                :description,
                '{}'::jsonb,
                :bookedAt,
                :bookedAt
            )
            RETURNING id
            """,
            params,
            Long.class);

    if (entryId == null) {
      throw new IllegalStateException("opening ledger insert did not return an id");
    }
    return entryId;
  }

  private void insertOpeningReadModel(
      long ledgerEntryId,
      long accountId,
      String accountNumber,
      AccountBootstrapCommand command,
      Instant bookedAt) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("ledgerEntryId", ledgerEntryId)
            .addValue("accountId", accountId)
            .addValue("transactionReference", "OPEN-" + accountNumber)
            .addValue("direction", CREDIT)
            .addValue("transactionStatus", BOOKED)
            .addValue("amountMinor", command.initialBalanceMinor())
            .addValue("balanceAfterMinor", command.initialBalanceMinor())
            .addValue("currencyCode", command.currencyCode())
            .addValue("summary", OPENING_SUMMARY)
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
            booked_at,
            created_at
        )
        VALUES (
            :ledgerEntryId,
            :accountId,
            :transactionReference,
            :direction,
            :transactionStatus,
            :amountMinor,
            :balanceAfterMinor,
            :currencyCode,
            :summary,
            :bookedAt,
            :bookedAt
        )
        """,
        params);
  }

  private void insertBalanceSnapshot(
      long accountId, long lastAppliedLedgerEntryId, AccountBootstrapCommand command, Instant now) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("accountId", accountId)
            .addValue("lastAppliedLedgerEntryId", lastAppliedLedgerEntryId)
            .addValue("availableBalanceMinor", command.initialBalanceMinor())
            .addValue("currencyCode", command.currencyCode())
            .addValue("now", Timestamp.from(now));

    jdbcTemplate.update(
        """
        INSERT INTO account_balance_snapshot (
            account_id,
            last_applied_ledger_entry_id,
            available_balance_minor,
            pending_balance_minor,
            currency_code,
            updated_at
        )
        VALUES (
            :accountId,
            :lastAppliedLedgerEntryId,
            :availableBalanceMinor,
            0,
            :currencyCode,
            :now
        )
        """,
        params);
  }
}
