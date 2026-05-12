package com.aquilabank.global.persistence.account;

import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.port.AccountSummaryReadPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 고객 계좌 단건 조회를 bank_account + snapshot exact lookup으로 읽어옵니다. */
@Repository
public class JdbcAccountSummaryRepository implements AccountSummaryReadPort {

  private static final String ACCOUNT_SUMMARY_SELECT =
      """
      SELECT account.id,
             account.account_number,
             account.display_name,
             account.account_status,
             account.currency_code AS account_currency_code,
             snapshot.available_balance_minor,
             snapshot.pending_balance_minor,
             snapshot.currency_code AS snapshot_currency_code,
             account.created_at,
             snapshot.updated_at
      FROM bank_account account
      JOIN account_balance_snapshot snapshot
        ON snapshot.account_id = account.id
      """;

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcAccountSummaryRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AccountSummary> findByAccountId(long accountId) {
    // account_id PK exact lookup이라 account 원본과 snapshot을 한 번만 join 합니다.
    return queryOne(
        ACCOUNT_SUMMARY_SELECT + "WHERE account.id = :accountId",
        new MapSqlParameterSource().addValue("accountId", accountId));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AccountSummary> findByAccountNumber(String accountNumber) {
    // account_number unique index exact lookup으로 수취인 탐색 비용과 노출 범위를 낮춥니다.
    return queryOne(
        ACCOUNT_SUMMARY_SELECT + "WHERE account.account_number = :accountNumber",
        new MapSqlParameterSource().addValue("accountNumber", accountNumber));
  }

  private Optional<AccountSummary> queryOne(String sql, MapSqlParameterSource parameters) {
    return jdbcTemplate.query(sql, parameters, (rs, rowNum) -> mapAccountSummary(rs)).stream()
        .findFirst();
  }

  private AccountSummary mapAccountSummary(ResultSet rs) throws SQLException {
    String accountCurrencyCode = rs.getString("account_currency_code");
    String snapshotCurrencyCode = rs.getString("snapshot_currency_code");
    if (!accountCurrencyCode.equals(snapshotCurrencyCode)) {
      throw new IllegalStateException("account snapshot currency does not match");
    }

    return new AccountSummary(
        rs.getLong("id"),
        rs.getString("account_number"),
        rs.getString("display_name"),
        rs.getString("account_status"),
        accountCurrencyCode,
        rs.getLong("available_balance_minor"),
        rs.getLong("pending_balance_minor"),
        toInstant(rs.getTimestamp("created_at")),
        toInstant(rs.getTimestamp("updated_at")));
  }

  private Instant toInstant(Timestamp timestamp) {
    if (timestamp == null) {
      throw new IllegalStateException("timestamp must not be null");
    }
    return timestamp.toInstant();
  }
}
