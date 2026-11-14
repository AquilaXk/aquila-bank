package com.aquilabank.global.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.domain.transaction.model.TransactionDirection;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

class JdbcTransactionReadRepositoryQueryShapeTest {

  private static final Instant FROM = Instant.parse("2026-04-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-05-01T00:00:00Z");
  private static final TransactionCursor CURSOR =
      new TransactionCursor(Instant.parse("2026-04-15T00:00:00Z"), 100L);

  @Test
  void timelineCursorShapeSeparatesImmediateAndDeepCursor() {
    assertThat(JdbcTransactionReadRepository.queryShape(query(null))).isEqualTo("first_page");
    assertThat(
            JdbcTransactionReadRepository.queryShape(
                query(new TransactionCursor(Instant.parse("2026-04-30T23:50:00Z"), 100L))))
        .isEqualTo("immediate_cursor");
    assertThat(JdbcTransactionReadRepository.queryShape(query(CURSOR))).isEqualTo("deep_cursor");
    assertThat(
            JdbcTransactionReadRepository.queryShape(
                new TransactionQuery(11L, FROM, TO, 50, null, null, null, null, null, "REF-1")))
        .isEqualTo("reference_exact");
    assertThat(
            JdbcTransactionReadRepository.queryShape(
                new TransactionQuery(
                    11L, FROM, TO, 50, null, TransactionStatus.BOOKED, null, null, null, null)))
        .isEqualTo("status_first");
    assertThat(
            JdbcTransactionReadRepository.queryShape(
                new TransactionQuery(
                    11L, FROM, TO, 50, CURSOR, TransactionStatus.BOOKED, null, null, null, null)))
        .isEqualTo("status_cursor");
    assertThat(
            JdbcTransactionReadRepository.queryShape(
                new TransactionQuery(
                    11L, FROM, TO, 50, null, null, TransactionDirection.DEBIT, null, null, null)))
        .isEqualTo("direction_first");
    assertThat(
            JdbcTransactionReadRepository.queryShape(
                new TransactionQuery(
                    11L, FROM, TO, 50, CURSOR, null, TransactionDirection.DEBIT, null, null, null)))
        .isEqualTo("direction_cursor");
    assertThat(
            JdbcTransactionReadRepository.queryShape(
                new TransactionQuery(11L, FROM, TO, 50, null, null, null, 100L, null, null)))
        .isEqualTo("amount_first");
    assertThat(
            JdbcTransactionReadRepository.queryShape(
                new TransactionQuery(11L, FROM, TO, 50, CURSOR, null, null, null, 500L, null)))
        .isEqualTo("amount_cursor");
    assertThat(
            JdbcTransactionReadRepository.queryShape(
                new TransactionQuery(
                    11L,
                    FROM,
                    TO,
                    50,
                    null,
                    TransactionStatus.BOOKED,
                    TransactionDirection.DEBIT,
                    null,
                    null,
                    null)))
        .isEqualTo("mixed_first");
    assertThat(
            JdbcTransactionReadRepository.queryShape(
                new TransactionQuery(
                    11L,
                    FROM,
                    TO,
                    50,
                    CURSOR,
                    TransactionStatus.BOOKED,
                    TransactionDirection.DEBIT,
                    100L,
                    null,
                    null)))
        .isEqualTo("mixed_cursor");
  }

  @Test
  @SuppressWarnings("unchecked")
  void preRegistersAllLatencyTimerCombinationsAndRecordsOutcome() {
    NamedParameterJdbcTemplate jdbcTemplate = mock(NamedParameterJdbcTemplate.class);
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    JdbcTransactionReadRepository repository =
        new JdbcTransactionReadRepository(jdbcTemplate, meterRegistry);

    assertThat(meterRegistry.find("aquila.transaction.query.latency").timers()).hasSize(24);

    when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
        .thenReturn(List.of())
        .thenThrow(new IllegalStateException("db timeout"));

    repository.fetch(query(null));
    assertThat(
            meterRegistry
                .get("aquila.transaction.query.latency")
                .tags("query_shape", "first_page", "outcome", "success")
                .timer()
                .count())
        .isEqualTo(1L);

    assertThatThrownBy(() -> repository.fetch(query(null)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(
            meterRegistry
                .get("aquila.transaction.query.latency")
                .tags("query_shape", "first_page", "outcome", "error")
                .timer()
                .count())
        .isEqualTo(1L);
  }

  private static TransactionQuery query(TransactionCursor cursor) {
    return new TransactionQuery(11L, FROM, TO, 50, cursor, null, null, null, null, null);
  }
}
