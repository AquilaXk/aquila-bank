package com.aquilabank.support;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** archive 거래 조회 baseline 테스트에서 cold table 데이터 분포를 반복 재현하는 fixture */
public final class TransactionArchiveReadModelBaselineFixture {

  private static final String ACTIVE = "ACTIVE";
  private static final String CURRENCY_CODE = "KRW";
  private static final Instant WINDOW_START = Instant.parse("2025-04-01T00:00:00Z");
  private static final Instant WINDOW_END = Instant.parse("2025-05-01T00:00:00Z");
  private static final Instant ARCHIVED_AT = Instant.parse("2026-04-01T00:00:00Z");
  private static final long OPENING_BALANCE_MINOR = 1_000_000L;
  private static final int HOT_ACCOUNT_ROW_COUNT = 80_000;
  private static final int HOT_ACCOUNT_STEP_SECONDS = 30;
  private static final int NOISE_ACCOUNT_COUNT = 6;
  private static final int NOISE_ACCOUNT_ROW_COUNT = 4_000;
  private static final int NOISE_ACCOUNT_STEP_SECONDS = 360;
  private static final int INSERT_BATCH_SIZE = 5_000;

  public BaselineWindow seed(NamedParameterJdbcTemplate jdbcTemplate) {
    long hotAccountId = insertAccount(jdbcTemplate, "archive-baseline-hot-account", WINDOW_START);
    insertArchiveRows(
        jdbcTemplate,
        hotAccountId,
        HOT_ACCOUNT_ROW_COUNT,
        HOT_ACCOUNT_STEP_SECONDS,
        "archive-hot-account");

    List<Long> noiseAccountIds = new ArrayList<>();
    for (int index = 1; index <= NOISE_ACCOUNT_COUNT; index++) {
      long accountId =
          insertAccount(jdbcTemplate, "archive-baseline-noise-account-" + index, WINDOW_START);
      insertArchiveRows(
          jdbcTemplate,
          accountId,
          NOISE_ACCOUNT_ROW_COUNT,
          NOISE_ACCOUNT_STEP_SECONDS,
          "archive-noise-account-" + index);
      noiseAccountIds.add(accountId);
    }

    analyzeTables(jdbcTemplate);
    return new BaselineWindow(hotAccountId, noiseAccountIds, WINDOW_START, WINDOW_END);
  }

  private long insertAccount(
      NamedParameterJdbcTemplate jdbcTemplate, String displayName, Instant createdAt) {
    String accountNumber =
        jdbcTemplate.queryForObject(
            """
            SELECT '100' || LPAD(nextval('bank_account_number_seq')::text, 11, '0')
            """,
            new MapSqlParameterSource(),
            String.class);
    if (accountNumber == null) {
      throw new IllegalStateException("archive baseline account number is not generated");
    }

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
                :createdAt,
                :createdAt
            )
            RETURNING id
            """,
            new MapSqlParameterSource()
                .addValue("accountNumber", accountNumber)
                .addValue("displayName", displayName)
                .addValue("accountStatus", ACTIVE)
                .addValue("currencyCode", CURRENCY_CODE)
                .addValue("createdAt", Timestamp.from(createdAt)),
            Long.class);
    if (accountId == null) {
      throw new IllegalStateException("archive baseline account insert did not return id");
    }
    return accountId;
  }

  private void insertArchiveRows(
      NamedParameterJdbcTemplate jdbcTemplate,
      long accountId,
      int rowCount,
      int stepSeconds,
      String referencePrefix) {
    for (int startSeq = 1; startSeq <= rowCount; startSeq += INSERT_BATCH_SIZE) {
      int endSeq = Math.min(startSeq + INSERT_BATCH_SIZE - 1, rowCount);
      jdbcTemplate.update(
          """
          WITH generated AS (
              SELECT
                  :accountId AS account_id,
                  series AS seq,
                  CAST(:windowStart AS timestamptz)
                      + make_interval(secs => ((series - 1) * :stepSeconds)::integer) AS booked_at,
                  CASE WHEN mod(series, 7) = 0 THEN 'DEBIT' ELSE 'CREDIT' END AS direction,
                  CASE
                      WHEN mod(series, 53) = 0 THEN 'REVERSED'
                      WHEN mod(series, 5) = 0 THEN 'PENDING'
                      ELSE 'BOOKED'
                  END AS transaction_status,
                  CASE WHEN mod(series, 7) = 0 THEN 1300 ELSE 1700 END AS amount_minor,
                  :openingBalanceMinor
                      + ((series - (series / 7)) * 1700)
                      - ((series / 7) * 1300) AS balance_after_minor,
                  :referencePrefix || '-trx-' || series AS transaction_reference,
                  :referencePrefix || '-entry-' || series AS entry_reference,
                  'archive-seed-' || :referencePrefix || '-' || series AS summary,
                  CASE WHEN mod(series, 3) = 0 THEN 'MERCHANT' ELSE 'ATM' END
                      AS counterparty_masked_name
              FROM generate_series(:startSeq, :endSeq) AS series
          ),
          inserted_ledger AS (
              INSERT INTO ledger_entry (
                  account_id,
                  transaction_reference,
                  entry_reference,
                  direction,
                  entry_status,
                  amount_minor,
                  currency_code,
                  booked_at,
                  occurred_at,
                  description,
                  metadata,
                  created_at,
                  updated_at
              )
              SELECT
                  account_id,
                  transaction_reference,
                  entry_reference,
                  direction,
                  transaction_status,
                  amount_minor,
                  :currencyCode,
                  booked_at,
                  booked_at,
                  summary,
                  '{}'::jsonb,
                  booked_at,
                  booked_at
              FROM generated
              RETURNING id, entry_reference
          )
          INSERT INTO transaction_read_model_archive (
              id,
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
              created_at,
              archived_at
          )
          SELECT
              inserted_ledger.id,
              inserted_ledger.id,
              generated.account_id,
              generated.transaction_reference,
              generated.direction,
              generated.transaction_status,
              generated.amount_minor,
              generated.balance_after_minor,
              :currencyCode,
              generated.summary,
              generated.counterparty_masked_name,
              generated.booked_at,
              generated.booked_at,
              :archivedAt
          FROM generated
          JOIN inserted_ledger
            ON inserted_ledger.entry_reference = generated.entry_reference
          """,
          new MapSqlParameterSource()
              .addValue("accountId", accountId)
              .addValue("currencyCode", CURRENCY_CODE)
              .addValue("referencePrefix", referencePrefix)
              .addValue("openingBalanceMinor", OPENING_BALANCE_MINOR)
              .addValue("startSeq", startSeq)
              .addValue("endSeq", endSeq)
              .addValue("stepSeconds", stepSeconds)
              .addValue("windowStart", Timestamp.from(WINDOW_START))
              .addValue("archivedAt", Timestamp.from(ARCHIVED_AT)));
    }
  }

  private void analyzeTables(NamedParameterJdbcTemplate jdbcTemplate) {
    jdbcTemplate.getJdbcTemplate().execute("ANALYZE bank_account");
    jdbcTemplate.getJdbcTemplate().execute("ANALYZE ledger_entry");
    jdbcTemplate.getJdbcTemplate().execute("ANALYZE transaction_read_model_archive");
  }

  public record BaselineWindow(
      long hotAccountId, List<Long> noiseAccountIds, Instant from, Instant to) {}
}
