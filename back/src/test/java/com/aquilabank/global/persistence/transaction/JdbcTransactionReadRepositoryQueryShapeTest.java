package com.aquilabank.global.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JdbcTransactionReadRepositoryQueryShapeTest {

  private static final Instant FROM = Instant.parse("2026-04-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-05-01T00:00:00Z");

  @Test
  void timelineCursorShapeSeparatesImmediateAndDeepCursor() {
    assertThat(JdbcTransactionReadRepository.queryShape(query(null))).isEqualTo("first_page");
    assertThat(
            JdbcTransactionReadRepository.queryShape(
                query(new TransactionCursor(Instant.parse("2026-04-30T23:50:00Z"), 100L))))
        .isEqualTo("immediate_cursor");
    assertThat(
            JdbcTransactionReadRepository.queryShape(
                query(new TransactionCursor(Instant.parse("2026-04-15T00:00:00Z"), 100L))))
        .isEqualTo("deep_cursor");
  }

  private static TransactionQuery query(TransactionCursor cursor) {
    return new TransactionQuery(11L, FROM, TO, 50, cursor, null, null, null, null, null);
  }
}
