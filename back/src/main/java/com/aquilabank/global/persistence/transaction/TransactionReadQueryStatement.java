package com.aquilabank.global.persistence.transaction;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import java.sql.Timestamp;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

/** 거래 조회 SQL을 6-비트 shape 테이블(64슬롯)로 고정해 StringBuilder 할당 없이 PreparedStatement 캐시를 재사용합니다. */
final class TransactionReadQueryStatement {

  private static final int BIT_CURSOR = 1;
  private static final int BIT_STATUS = 1 << 1;
  private static final int BIT_DIRECTION = 1 << 2;
  private static final int BIT_MIN_AMOUNT = 1 << 3;
  private static final int BIT_MAX_AMOUNT = 1 << 4;
  private static final int BIT_TRANSACTION_REFERENCE = 1 << 5;
  private static final int SQL_SHAPE_COUNT = 1 << 6;
  private static final String[] HOT_SQL_TABLE =
      buildSqlTable("hot-timeline", "transaction_read_model");

  private final String sql;
  private final MapSqlParameterSource params;

  private TransactionReadQueryStatement(String sql, MapSqlParameterSource params) {
    this.sql = sql;
    this.params = params;
  }

  static TransactionReadQueryStatement from(TransactionQuery query) {
    return new TransactionReadQueryStatement(
        HOT_SQL_TABLE[shapeMask(query)], bindParameters(query));
  }

  static int shapeMask(TransactionQuery query) {
    int mask = 0;
    if (query.cursor() != null) {
      mask |= BIT_CURSOR;
    }
    if (query.status() != null) {
      mask |= BIT_STATUS;
    }
    if (query.direction() != null) {
      mask |= BIT_DIRECTION;
    }
    if (query.minAmountMinor() != null) {
      mask |= BIT_MIN_AMOUNT;
    }
    if (query.maxAmountMinor() != null) {
      mask |= BIT_MAX_AMOUNT;
    }
    if (query.transactionReference() != null) {
      mask |= BIT_TRANSACTION_REFERENCE;
    }
    return mask;
  }

  static MapSqlParameterSource bindParameters(TransactionQuery query) {
    MapSqlParameterSource params =
        new MapSqlParameterSource()
            .addValue("accountId", query.accountId())
            .addValue("from", Timestamp.from(query.from()))
            .addValue("effectiveTo", Timestamp.from(effectiveTo(query)))
            // 다음 page 존재 여부 판단용 sentinel row 한 건 추가 조회
            .addValue("fetchLimit", query.limit() + 1);
    if (query.status() != null) {
      params.addValue("status", query.status().name());
    }
    if (query.direction() != null) {
      params.addValue("direction", query.direction().name());
    }
    if (query.minAmountMinor() != null) {
      params.addValue("minAmountMinor", query.minAmountMinor());
    }
    if (query.maxAmountMinor() != null) {
      params.addValue("maxAmountMinor", query.maxAmountMinor());
    }
    if (query.transactionReference() != null) {
      params.addValue("transactionReference", query.transactionReference());
    }
    if (query.cursor() != null) {
      params
          .addValue("cursorBookedAt", Timestamp.from(query.cursor().bookedAt()))
          .addValue("cursorId", query.cursor().id());
    }
    return params;
  }

  static String[] buildSqlTable(String traceShape, String tableName) {
    String[] table = new String[SQL_SHAPE_COUNT];
    String header = TransactionReadSqlTraceComment.current(traceShape);
    for (int mask = 0; mask < SQL_SHAPE_COUNT; mask++) {
      table[mask] = buildSqlForMask(header, tableName, mask);
    }
    return table;
  }

  private static String buildSqlForMask(String header, String tableName, int mask) {
    StringBuilder sql =
        new StringBuilder(header)
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
            FROM\s""")
            .append(tableName)
            .append(
                """

            WHERE account_id = :accountId
              AND booked_at >= :from
            """);

    boolean hasCursor = (mask & BIT_CURSOR) != 0;
    if (!hasCursor) {
      sql.append("\n  AND booked_at < :effectiveTo");
    } else {
      // cursor page는 요청 to보다 cursor 시각이 실제 상한입니다. 같은 booked_at의 id tie-breaker는 tuple predicate가
      // 보존합니다.
      sql.append("\n  AND booked_at <= :effectiveTo");
    }
    if ((mask & BIT_STATUS) != 0) {
      sql.append("\n  AND transaction_status = :status");
    }
    if ((mask & BIT_DIRECTION) != 0) {
      sql.append("\n  AND direction = :direction");
    }
    if ((mask & BIT_MIN_AMOUNT) != 0) {
      sql.append("\n  AND amount_minor >= :minAmountMinor");
    }
    if ((mask & BIT_MAX_AMOUNT) != 0) {
      sql.append("\n  AND amount_minor <= :maxAmountMinor");
    }
    if ((mask & BIT_TRANSACTION_REFERENCE) != 0) {
      // transactionReference exact lookup은 account scope 안에서 먼저 좁혀 전용 exact index 경로를 타게 둡니다.
      sql.append("\n  AND transaction_reference = :transactionReference");
    }
    if (hasCursor) {
      // keyset cursor와 ORDER BY tuple을 같게 두어 composite index range scan으로 이어지게 합니다.
      sql.append("\n  AND (booked_at, id) < (:cursorBookedAt, :cursorId)");
    }
    sql.append(
        """

            ORDER BY booked_at DESC, id DESC
            LIMIT :fetchLimit
        """);
    return sql.toString();
  }

  String sql() {
    return sql;
  }

  MapSqlParameterSource params() {
    return params;
  }

  private static java.time.Instant effectiveTo(TransactionQuery query) {
    if (query.cursor() == null) {
      return query.to();
    }
    return query.cursor().bookedAt();
  }
}
