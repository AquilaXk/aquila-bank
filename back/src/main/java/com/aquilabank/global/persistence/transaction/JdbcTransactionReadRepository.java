package com.aquilabank.global.persistence.transaction;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.model.TransactionSummary;
import com.aquilabank.domain.transaction.port.TransactionReadPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 계좌 타임라인 조회를 keyset pagination 기준으로 읽어오는 JDBC adapter */
@Repository
public class JdbcTransactionReadRepository implements TransactionReadPort {

  private static final RowMapper<TransactionSummary> ROW_MAPPER =
      TransactionSummaryRowMapper.INSTANCE;
  private static final Duration DEEP_CURSOR_MIN_DISTANCE = Duration.ofHours(1);
  private static final long DEEP_CURSOR_WINDOW_DIVISOR = 4L;

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final MeterRegistry meterRegistry;

  public JdbcTransactionReadRepository(
      @Qualifier("transactionReadJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
      MeterRegistry meterRegistry) {
    this.jdbcTemplate = jdbcTemplate;
    this.meterRegistry = meterRegistry;
  }

  @Override
  @Transactional(readOnly = true, transactionManager = "transactionReadTransactionManager")
  public TransactionSlice fetch(TransactionQuery query) {
    Timer.Sample sample = Timer.start(meterRegistry);
    String outcome = "success";
    try {
      TransactionReadQueryStatement statement = TransactionReadQueryStatement.from(query);
      List<TransactionSummary> rows =
          jdbcTemplate.query(statement.sql(), statement.params(), ROW_MAPPER);
      boolean hasNext = rows.size() > query.limit();
      // hasNext 판단에만 쓴 sentinel row는 응답 전에 제거
      List<TransactionSummary> items =
          hasNext ? new ArrayList<>(rows.subList(0, query.limit())) : rows;
      TransactionCursor nextCursor = hasNext ? toCursor(items.getLast()) : null;
      return new TransactionSlice(items, nextCursor, hasNext, query.limit());
    } catch (RuntimeException ex) {
      outcome = "error";
      throw ex;
    } finally {
      sample.stop(
          meterRegistry.timer(
              "aquila.transaction.query.latency",
              "query_shape",
              queryShape(query),
              "outcome",
              outcome));
    }
  }

  private static TransactionCursor toCursor(TransactionSummary item) {
    // 현재 slice의 마지막 visible row 다음부터 다음 page가 시작되게 cursor 생성
    return new TransactionCursor(item.bookedAt(), item.id());
  }

  static String queryShape(TransactionQuery query) {
    boolean hasCursor = query.cursor() != null;
    boolean hasStatus = query.status() != null;
    boolean hasDirection = query.direction() != null;
    boolean hasAmountRange = query.minAmountMinor() != null || query.maxAmountMinor() != null;
    if (query.transactionReference() != null) {
      return "reference_exact";
    }
    if (hasStatus && !hasDirection && !hasAmountRange) {
      return hasCursor ? "status_cursor" : "status_first";
    }
    if (!hasStatus && hasDirection && !hasAmountRange) {
      return hasCursor ? "direction_cursor" : "direction_first";
    }
    if (!hasStatus && !hasDirection && hasAmountRange) {
      return hasCursor ? "amount_cursor" : "amount_first";
    }
    // tag cardinality를 낮추려고 filter 조합은 coarse bucket으로만 묶습니다.
    if (hasStatus || hasDirection || hasAmountRange) {
      return hasCursor ? "mixed_cursor" : "mixed_first";
    }
    if (!hasCursor) {
      return "first_page";
    }
    return isDeepTimelineCursor(query) ? "deep_cursor" : "immediate_cursor";
  }

  private static boolean isDeepTimelineCursor(TransactionQuery query) {
    if (!query.cursor().bookedAt().isBefore(query.to())) {
      return false;
    }
    Duration window = Duration.between(query.from(), query.to());
    if (window.isZero() || window.isNegative()) {
      return false;
    }
    Duration distanceFromUpperBound = Duration.between(query.cursor().bookedAt(), query.to());
    // 첫 page 직후 cursor와 중간 기간 cursor를 분리해 deep seek 회귀를 별도 SLO로 잡습니다.
    Duration deepBoundary =
        maxDuration(window.dividedBy(DEEP_CURSOR_WINDOW_DIVISOR), DEEP_CURSOR_MIN_DISTANCE);
    return !distanceFromUpperBound.minus(deepBoundary).isNegative();
  }

  private static Duration maxDuration(Duration left, Duration right) {
    return left.compareTo(right) >= 0 ? left : right;
  }
}
