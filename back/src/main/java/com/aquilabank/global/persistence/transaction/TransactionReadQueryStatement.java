package com.aquilabank.global.persistence.transaction;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import java.sql.Timestamp;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** 거래 조회 SQL과 bind 파라미터를 한 곳에 모아 운영 경로와 EXPLAIN 기준선을 맞춥니다. */
final class TransactionReadQueryStatement {

  private final String sql;
  private final MapSqlParameterSource params;

  private TransactionReadQueryStatement(String sql, MapSqlParameterSource params) {
    this.sql = sql;
    this.params = params;
  }

  static TransactionReadQueryStatement from(TransactionQuery query) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("accountId", query.accountId())
            .addValue("from", Timestamp.from(query.from()))
            .addValue("to", Timestamp.from(query.to()))
            // 다음 page 존재 여부 판단용 sentinel row 한 건 추가 조회
            .addValue("fetchLimit", query.limit() + 1);

    StringBuilder sql =
        new StringBuilder(
            """
            SELECT id,
                   account_id,
                   transaction_reference,
                   direction,
                   transaction_status,
                   amount_minor,
                   balance_after_minor,
                   currency_code,
                   summary,
                   counterparty_masked_name,
                   booked_at
            FROM transaction_read_model
            WHERE account_id = :accountId
              AND booked_at >= :from
              AND booked_at < :to
            """);

    if (query.status() != null) {
      sql.append("\n  AND transaction_status = :status");
      params.addValue("status", query.status().name());
    }

    if (query.cursor() != null) {
      // keyset cursor와 ORDER BY tuple을 같게 두어 composite index range scan으로 이어지게 합니다.
      sql.append("\n  AND (booked_at, id) < (:cursorBookedAt, :cursorId)");
      params
          .addValue("cursorBookedAt", Timestamp.from(query.cursor().bookedAt()))
          .addValue("cursorId", query.cursor().id());
    }

    sql.append(
        """

            ORDER BY booked_at DESC, id DESC
            LIMIT :fetchLimit
        """);
    return new TransactionReadQueryStatement(sql.toString(), params);
  }

  String sql() {
    return sql;
  }

  MapSqlParameterSource params() {
    return params;
  }
}
