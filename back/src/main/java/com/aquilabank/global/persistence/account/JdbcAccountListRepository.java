package com.aquilabank.global.persistence.account;

import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.model.AccountSummaryList;
import com.aquilabank.domain.account.port.AccountListReadPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 내 계좌 목록 조회를 membership 범위와 snapshot join 한 번으로 읽어옵니다. */
@Repository
public class JdbcAccountListRepository implements AccountListReadPort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcAccountListRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional(readOnly = true)
  public AccountSummaryList findByUserId(long userId) {
    return new AccountSummaryList(
        jdbcTemplate.query(
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
            FROM user_account_membership membership
            JOIN bank_user bank_user
              ON bank_user.id = membership.user_id
            JOIN bank_account account
              ON account.id = membership.account_id
            JOIN account_balance_snapshot snapshot
              ON snapshot.account_id = account.id
            WHERE membership.user_id = :userId
              AND membership.membership_status = 'ACTIVE'
              AND bank_user.user_status = 'ACTIVE'
            ORDER BY membership.account_id ASC
            """,
            new MapSqlParameterSource().addValue("userId", userId),
            (rs, rowNum) -> mapAccountSummary(rs)));
  }

  @Override
  @Transactional(readOnly = true)
  public AccountSummaryList findByUserId(long userId, int limit, Long afterAccountId) {
    int fetchSize = limit + 1;
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("userId", userId)
            .addValue("limit", fetchSize)
            .addValue("afterAccountId", afterAccountId == null ? 0L : afterAccountId);
    List<AccountSummary> items =
        jdbcTemplate.query(
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
            FROM user_account_membership membership
            JOIN bank_user bank_user
              ON bank_user.id = membership.user_id
            JOIN bank_account account
              ON account.id = membership.account_id
            JOIN account_balance_snapshot snapshot
              ON snapshot.account_id = account.id
            WHERE membership.user_id = :userId
              AND membership.membership_status = 'ACTIVE'
              AND membership.account_id > :afterAccountId
              AND bank_user.user_status = 'ACTIVE'
            ORDER BY membership.account_id ASC
            LIMIT :limit
            """,
            params,
            (rs, rowNum) -> mapAccountSummary(rs));
    if (items.size() <= limit) {
      return new AccountSummaryList(items);
    }
    List<AccountSummary> pageItems = items.subList(0, limit);
    return new AccountSummaryList(pageItems, pageItems.getLast().accountId());
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
