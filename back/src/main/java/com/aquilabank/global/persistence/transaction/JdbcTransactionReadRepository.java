package com.aquilabank.global.persistence.transaction;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.model.TransactionSummary;
import com.aquilabank.domain.transaction.port.TransactionReadPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
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
  private static final String[] QUERY_SHAPES = {
    "reference_exact",
    "status_cursor",
    "status_first",
    "direction_cursor",
    "direction_first",
    "amount_cursor",
    "amount_first",
    "mixed_cursor",
    "mixed_first",
    "first_page",
    "deep_cursor",
    "immediate_cursor"
  };
  private static final String[] OUTCOMES = {"success", "error"};
  private static final int SHAPE_REFERENCE_EXACT = 0;
  private static final int SHAPE_STATUS_CURSOR = 1;
  private static final int SHAPE_STATUS_FIRST = 2;
  private static final int SHAPE_DIRECTION_CURSOR = 3;
  private static final int SHAPE_DIRECTION_FIRST = 4;
  private static final int SHAPE_AMOUNT_CURSOR = 5;
  private static final int SHAPE_AMOUNT_FIRST = 6;
  private static final int SHAPE_MIXED_CURSOR = 7;
  private static final int SHAPE_MIXED_FIRST = 8;
  private static final int SHAPE_FIRST_PAGE = 9;
  private static final int SHAPE_DEEP_CURSOR = 10;
  private static final int SHAPE_IMMEDIATE_CURSOR = 11;
  private static final int OUTCOME_SUCCESS = 0;
  private static final int OUTCOME_ERROR = 1;

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final MeterRegistry meterRegistry;
  private final Timer[][] latencyTimers;

  public JdbcTransactionReadRepository(
      @Qualifier("transactionReadJdbcTemplate") NamedParameterJdbcTemplate jdbcTemplate,
      MeterRegistry meterRegistry) {
    this.jdbcTemplate = jdbcTemplate;
    this.meterRegistry = meterRegistry;
    // shape x outcome 조합별 Timer를 생성 시점에 선등록해 조회 핫패스의 tag 배열 할당과 registry 조회를 제거합니다.
    Timer[][] matrix = new Timer[QUERY_SHAPES.length][OUTCOMES.length];
    for (int shapeIndex = 0; shapeIndex < QUERY_SHAPES.length; shapeIndex++) {
      for (int outcomeIndex = 0; outcomeIndex < OUTCOMES.length; outcomeIndex++) {
        matrix[shapeIndex][outcomeIndex] =
            meterRegistry.timer(
                "aquila.transaction.query.latency",
                "query_shape",
                QUERY_SHAPES[shapeIndex],
                "outcome",
                OUTCOMES[outcomeIndex]);
      }
    }
    this.latencyTimers = matrix;
  }

  @Override
  @Transactional(readOnly = true, transactionManager = "transactionReadTransactionManager")
  public TransactionSlice fetch(TransactionQuery query) {
    Timer.Sample sample = Timer.start(meterRegistry);
    int shapeIndex = queryShapeIndex(query);
    int outcomeIndex = OUTCOME_SUCCESS;
    try {
      TransactionReadQueryStatement statement = TransactionReadQueryStatement.from(query);
      List<TransactionSummary> rows =
          jdbcTemplate.query(statement.sql(), statement.params(), ROW_MAPPER);
      boolean hasNext = rows.size() > query.limit();
      // hasNext 판단에만 쓴 sentinel row는 응답 전에 제거 (TransactionSlice.copyOf에서 단일 불변 복사 수행)
      List<TransactionSummary> items = hasNext ? rows.subList(0, query.limit()) : rows;
      TransactionCursor nextCursor = hasNext ? toCursor(items.getLast()) : null;
      return new TransactionSlice(items, nextCursor, hasNext, query.limit());
    } catch (RuntimeException ex) {
      outcomeIndex = OUTCOME_ERROR;
      throw ex;
    } finally {
      sample.stop(latencyTimers[shapeIndex][outcomeIndex]);
    }
  }

  private static TransactionCursor toCursor(TransactionSummary item) {
    // 현재 slice의 마지막 visible row 다음부터 다음 page가 시작되게 cursor 생성
    return new TransactionCursor(item.bookedAt(), item.id());
  }

  static String queryShape(TransactionQuery query) {
    return QUERY_SHAPES[queryShapeIndex(query)];
  }

  private static int queryShapeIndex(TransactionQuery query) {
    boolean hasCursor = query.cursor() != null;
    boolean hasStatus = query.status() != null;
    boolean hasDirection = query.direction() != null;
    boolean hasAmountRange = query.minAmountMinor() != null || query.maxAmountMinor() != null;
    if (query.transactionReference() != null) {
      return SHAPE_REFERENCE_EXACT;
    }
    if (hasStatus && !hasDirection && !hasAmountRange) {
      return hasCursor ? SHAPE_STATUS_CURSOR : SHAPE_STATUS_FIRST;
    }
    if (!hasStatus && hasDirection && !hasAmountRange) {
      return hasCursor ? SHAPE_DIRECTION_CURSOR : SHAPE_DIRECTION_FIRST;
    }
    if (!hasStatus && !hasDirection && hasAmountRange) {
      return hasCursor ? SHAPE_AMOUNT_CURSOR : SHAPE_AMOUNT_FIRST;
    }
    // tag cardinality를 낮추려고 filter 조합은 coarse bucket으로만 묶습니다.
    if (hasStatus || hasDirection || hasAmountRange) {
      return hasCursor ? SHAPE_MIXED_CURSOR : SHAPE_MIXED_FIRST;
    }
    if (!hasCursor) {
      return SHAPE_FIRST_PAGE;
    }
    return isDeepTimelineCursor(query) ? SHAPE_DEEP_CURSOR : SHAPE_IMMEDIATE_CURSOR;
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
