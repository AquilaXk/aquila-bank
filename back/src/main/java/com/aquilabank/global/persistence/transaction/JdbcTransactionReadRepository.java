package com.aquilabank.global.persistence.transaction;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.domain.transaction.model.TransactionDirection;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import com.aquilabank.domain.transaction.model.TransactionSummary;
import com.aquilabank.domain.transaction.port.TransactionReadPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 계좌 타임라인 조회를 keyset pagination 기준으로 읽어오는 JDBC adapter */
@Repository
public class JdbcTransactionReadRepository implements TransactionReadPort {

  private static final RowMapper<TransactionSummary> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcTransactionReadRepository(NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional(readOnly = true)
  public TransactionSlice fetch(TransactionQuery query) {
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
      // keyset pagination은 같은 ORDER BY 컬럼을 재사용해 깊은 OFFSET scan 회피
      sql.append(
          """

              AND (
                    booked_at < :cursorBookedAt
                 OR (booked_at = :cursorBookedAt AND id < :cursorId)
              )
          """);
      params
          .addValue("cursorBookedAt", Timestamp.from(query.cursor().bookedAt()))
          .addValue("cursorId", query.cursor().id());
    }

    sql.append(
        """

            ORDER BY booked_at DESC, id DESC
            LIMIT :fetchLimit
        """);

    List<TransactionSummary> rows = jdbcTemplate.query(sql.toString(), params, ROW_MAPPER);
    boolean hasNext = rows.size() > query.limit();
    // hasNext 판단에만 쓴 sentinel row는 응답 전에 제거
    List<TransactionSummary> items =
        hasNext ? new ArrayList<>(rows.subList(0, query.limit())) : rows;
    TransactionCursor nextCursor = hasNext ? toCursor(items.getLast()) : null;
    return new TransactionSlice(items, nextCursor, hasNext, query.limit());
  }

  private static TransactionSummary mapRow(ResultSet rs) throws SQLException {
    OffsetDateTime bookedAt = rs.getObject("booked_at", OffsetDateTime.class);
    return new TransactionSummary(
        rs.getLong("id"),
        rs.getLong("account_id"),
        rs.getString("transaction_reference"),
        TransactionDirection.valueOf(rs.getString("direction")),
        TransactionStatus.valueOf(rs.getString("transaction_status")),
        rs.getLong("amount_minor"),
        rs.getLong("balance_after_minor"),
        rs.getString("currency_code"),
        rs.getString("summary"),
        rs.getString("counterparty_masked_name"),
        bookedAt.toInstant());
  }

  private static TransactionCursor toCursor(TransactionSummary item) {
    // 현재 slice의 마지막 visible row 다음부터 다음 page가 시작되게 cursor 생성
    return new TransactionCursor(item.bookedAt(), item.id());
  }
}
