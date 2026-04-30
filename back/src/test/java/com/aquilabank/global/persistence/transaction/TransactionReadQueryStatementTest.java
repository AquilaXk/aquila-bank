package com.aquilabank.global.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TransactionReadQueryStatementTest {

  private static final Instant FROM = Instant.parse("2026-04-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-05-01T00:00:00Z");
  private static final Instant CURSOR_AT = Instant.parse("2026-04-15T00:00:00Z");

  @Test
  void hotCursorQueryUsesCursorTimeAsEffectiveUpperBound() {
    TransactionReadQueryStatement statement =
        TransactionReadQueryStatement.from(query(new TransactionCursor(CURSOR_AT, Long.MAX_VALUE)));

    assertThat(statement.sql()).contains("AND booked_at <= :effectiveTo");
    assertThat(statement.sql()).doesNotContain("AND booked_at < :to");
    assertThat(statement.params().getValue("effectiveTo")).isEqualTo(Timestamp.from(CURSOR_AT));
  }

  @Test
  void archiveCursorQueryUsesCursorTimeAsEffectiveUpperBound() {
    TransactionArchiveReadQueryStatement statement =
        TransactionArchiveReadQueryStatement.from(
            query(new TransactionCursor(CURSOR_AT, Long.MAX_VALUE)));

    assertThat(statement.sql()).contains("AND booked_at <= :effectiveTo");
    assertThat(statement.sql()).doesNotContain("AND booked_at < :to");
    assertThat(statement.params().getValue("effectiveTo")).isEqualTo(Timestamp.from(CURSOR_AT));
  }

  @Test
  void firstPageQueryKeepsRequestUpperBound() {
    TransactionReadQueryStatement statement = TransactionReadQueryStatement.from(query(null));

    assertThat(statement.sql()).contains("AND booked_at < :effectiveTo");
    assertThat(statement.params().getValue("effectiveTo")).isEqualTo(Timestamp.from(TO));
  }

  private static TransactionQuery query(TransactionCursor cursor) {
    return new TransactionQuery(11L, FROM, TO, 50, cursor, null, null, null, null, null);
  }
}
