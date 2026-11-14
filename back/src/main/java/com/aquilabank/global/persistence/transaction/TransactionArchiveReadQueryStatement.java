package com.aquilabank.global.persistence.transaction;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** archive 거래 조회 SQL을 6-비트 shape 테이블(64슬롯)로 고정해 PreparedStatement 캐시 효율과 계약을 유지합니다. */
final class TransactionArchiveReadQueryStatement {

  private static final String[] ARCHIVE_SQL_TABLE =
      TransactionReadQueryStatement.buildSqlTable(
          "archive-timeline", "transaction_read_model_archive");

  private final String sql;
  private final MapSqlParameterSource params;

  private TransactionArchiveReadQueryStatement(String sql, MapSqlParameterSource params) {
    this.sql = sql;
    this.params = params;
  }

  static TransactionArchiveReadQueryStatement from(TransactionQuery query) {
    return new TransactionArchiveReadQueryStatement(
        ARCHIVE_SQL_TABLE[TransactionReadQueryStatement.shapeMask(query)],
        TransactionReadQueryStatement.bindParameters(query));
  }

  String sql() {
    return sql;
  }

  MapSqlParameterSource params() {
    return params;
  }
}
