package com.aquilabank.global.persistence.transaction;

import com.aquilabank.domain.transaction.model.TransactionDetailQuery;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** 거래 상세 SQL과 bind 파라미터를 묶어 exact lookup query shape를 고정합니다. */
final class TransactionDetailQueryStatement {

  private final String sql;
  private final MapSqlParameterSource params;

  private TransactionDetailQueryStatement(String sql, MapSqlParameterSource params) {
    this.sql = sql;
    this.params = params;
  }

  static TransactionDetailQueryStatement from(TransactionDetailQuery query) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("accountId", query.accountId())
            .addValue("transactionReference", query.transactionReference())
            // 두 건 이상이면 정합성 이상으로 처리하기 위해 최대 두 건까지만 읽습니다.
            .addValue("rowLimit", 2);

    return new TransactionDetailQueryStatement(
        """
        SELECT trm.account_id,
               trm.transaction_reference,
               trm.direction,
               trm.transaction_status,
               trm.amount_minor,
               trm.balance_after_minor,
               trm.currency_code,
               trm.summary,
               trm.counterparty_masked_name,
               trm.booked_at,
               le.entry_reference,
               le.entry_status,
               le.occurred_at,
               le.description
        FROM ledger_entry le
        JOIN transaction_read_model trm
          ON trm.ledger_entry_id = le.id
        WHERE le.transaction_reference = :transactionReference
          AND le.account_id = :accountId
          -- source/target row가 같은 transactionReference를 공유하므로 account scope를 양쪽 테이블에 고정합니다.
          AND trm.account_id = :accountId
          AND trm.transaction_reference = :transactionReference
        ORDER BY le.id DESC
        LIMIT :rowLimit
        """,
        params);
  }

  String sql() {
    return sql;
  }

  MapSqlParameterSource params() {
    return params;
  }
}
