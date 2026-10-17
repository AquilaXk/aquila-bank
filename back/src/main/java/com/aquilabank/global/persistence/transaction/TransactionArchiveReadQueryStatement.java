package com.aquilabank.global.persistence.transaction;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** archive 거래 조회 SQL을 hot query와 같은 keyset 계약으로 고정합니다. */
final class TransactionArchiveReadQueryStatement {

  private final String sql;
  private final MapSqlParameterSource params;

  private TransactionArchiveReadQueryStatement(String sql, MapSqlParameterSource params) {
    this.sql = sql;
    this.params = params;
  }

  static TransactionArchiveReadQueryStatement from(TransactionQuery query) {
    TransactionReadQueryStatement delegate =
        TransactionReadQueryStatement.from(
            query, "archive-timeline", "transaction_read_model_archive");
    return new TransactionArchiveReadQueryStatement(delegate.sql(), delegate.params());
  }

  String sql() {
    return sql;
  }

  MapSqlParameterSource params() {
    return params;
  }
}
