package com.aquilabank.global.persistence.transaction;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.domain.transaction.model.TransactionDirection;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import com.aquilabank.domain.transaction.model.TransactionSummary;
import com.aquilabank.domain.transaction.port.TransactionArchiveReadPort;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** archive transaction timeline을 keyset pagination 기준으로 읽어오는 JDBC adapter */
@Repository
public class JdbcTransactionArchiveReadRepository implements TransactionArchiveReadPort {

  private static final RowMapper<TransactionSummary> ROW_MAPPER = (rs, rowNum) -> mapRow(rs);

  private final NamedParameterJdbcTemplate jdbcTemplate;

  public JdbcTransactionArchiveReadRepository(
      @Qualifier("transactionReadJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  @Override
  @Transactional(readOnly = true, transactionManager = "transactionReadTransactionManager")
  public TransactionSlice fetchArchived(TransactionQuery query) {
    TransactionArchiveReadQueryStatement statement =
        TransactionArchiveReadQueryStatement.from(query);
    List<TransactionSummary> rows =
        jdbcTemplate.query(statement.sql(), statement.params(), ROW_MAPPER);
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
