package com.aquilabank.global.persistence.ledger;

import com.aquilabank.domain.ledger.port.TransferLimitUsageReadPort;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/** source 계좌의 당일 BOOKED DEBIT 합계만 읽어 daily limit 검증 비용을 제한합니다. */
@Repository
public class JdbcTransferLimitUsageRepository implements TransferLimitUsageReadPort {

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcTransferLimitUsageRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  public long sumBookedDebitAmountMinor(
      long sourceAccountId, String currencyCode, Instant fromInclusive, Instant toExclusive) {
    Long total =
        jdbcTemplate.queryForObject(
            """
            SELECT COALESCE(SUM(amount_minor), 0)::bigint
            FROM ledger_entry
            WHERE account_id = :sourceAccountId
              AND currency_code = :currencyCode
              AND direction = 'DEBIT'
              AND entry_status = 'BOOKED'
              AND booked_at >= :fromInclusive
              AND booked_at < :toExclusive
            """,
            new MapSqlParameterSource()
                .addValue("sourceAccountId", sourceAccountId)
                .addValue("currencyCode", currencyCode)
                .addValue("fromInclusive", Timestamp.from(fromInclusive))
                .addValue("toExclusive", Timestamp.from(toExclusive)),
            Long.class);
    return total == null ? 0L : total;
  }
}
