package com.aquilabank.global.persistence.transaction;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import java.sql.Timestamp;
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
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("accountId", query.accountId())
            .addValue("from", Timestamp.from(query.from()))
            .addValue("effectiveTo", Timestamp.from(effectiveTo(query)))
            // 다음 page 존재 여부 판단용 sentinel row 한 건 추가 조회
            .addValue("fetchLimit", query.limit() + 1);

    StringBuilder sql =
        new StringBuilder(TransactionReadSqlTraceComment.current("archive-timeline"))
            .append(
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
            FROM transaction_read_model_archive
            WHERE account_id = :accountId
              AND booked_at >= :from
            """);

    if (query.cursor() == null) {
      sql.append("\n  AND booked_at < :effectiveTo");
    } else {
      // archive deep cursor도 cursor 시각을 실제 상한으로 써 월별 partition range를 작게 유지합니다.
      sql.append("\n  AND booked_at <= :effectiveTo");
    }

    if (query.status() != null) {
      sql.append("\n  AND transaction_status = :status");
      params.addValue("status", query.status().name());
    }

    if (query.direction() != null) {
      sql.append("\n  AND direction = :direction");
      params.addValue("direction", query.direction().name());
    }

    if (query.minAmountMinor() != null) {
      sql.append("\n  AND amount_minor >= :minAmountMinor");
      params.addValue("minAmountMinor", query.minAmountMinor());
    }

    if (query.maxAmountMinor() != null) {
      sql.append("\n  AND amount_minor <= :maxAmountMinor");
      params.addValue("maxAmountMinor", query.maxAmountMinor());
    }

    if (query.transactionReference() != null) {
      // archive exact lookup도 전용 index로 account 범위 안에서 좁힙니다.
      sql.append("\n  AND transaction_reference = :transactionReference");
      params.addValue("transactionReference", query.transactionReference());
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
    return new TransactionArchiveReadQueryStatement(sql.toString(), params);
  }

  String sql() {
    return sql;
  }

  MapSqlParameterSource params() {
    return params;
  }

  private static java.time.Instant effectiveTo(TransactionQuery query) {
    if (query.cursor() == null || query.cursor().bookedAt().isAfter(query.to())) {
      return query.to();
    }
    return query.cursor().bookedAt();
  }
}
