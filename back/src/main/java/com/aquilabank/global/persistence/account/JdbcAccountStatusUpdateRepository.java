package com.aquilabank.global.persistence.account;

import com.aquilabank.domain.account.exception.AccountSummaryNotFoundException;
import com.aquilabank.domain.account.model.AccountStatusUpdateCommand;
import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.port.AccountStatusUpdatePort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 계좌 상태 변경과 응답 요약 조회를 한 adapter로 묶어 exact update 비용을 고정합니다. */
@Repository
public class JdbcAccountStatusUpdateRepository implements AccountStatusUpdatePort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcAccountStatusUpdateRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional
  public AccountSummary updateStatus(AccountStatusUpdateCommand command) {
    Instant updatedAt = Instant.now();
    return jdbcTemplate
        .query(
            """
            WITH updated_account AS (
                UPDATE bank_account
                SET account_status = :accountStatus,
                    updated_at = :updatedAt
                WHERE id = :accountId
                RETURNING id,
                          account_number,
                          display_name,
                          account_status,
                          currency_code,
                          created_at
            )
            SELECT updated_account.id,
                   updated_account.account_number,
                   updated_account.display_name,
                   updated_account.account_status,
                   updated_account.currency_code AS account_currency_code,
                   snapshot.available_balance_minor,
                   snapshot.pending_balance_minor,
                   snapshot.currency_code AS snapshot_currency_code,
                   updated_account.created_at,
                   snapshot.updated_at
            FROM updated_account
            JOIN account_balance_snapshot snapshot
              ON snapshot.account_id = updated_account.id
            """,
            new MapSqlParameterSource()
                .addValue("accountId", command.accountId())
                .addValue("accountStatus", command.status().name())
                .addValue("updatedAt", Timestamp.from(updatedAt)),
            (rs, rowNum) -> mapAccountSummary(rs))
        .stream()
        .findFirst()
        .orElseThrow(() -> new AccountSummaryNotFoundException("account summary is not found"));
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
