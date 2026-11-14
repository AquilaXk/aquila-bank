package com.aquilabank.global.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.global.web.RequestTraceContext;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TransactionReadQueryStatementTest {

  private static final Instant FROM = Instant.parse("2026-04-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-05-01T00:00:00Z");
  private static final Instant CURSOR_AT = Instant.parse("2026-04-15T00:00:00Z");

  @AfterEach
  void clearRequestTrace() {
    RequestTraceContext.clear();
  }

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

  @Test
  void queryStatementsIncludeStaticQueryShapeCommentWithoutRequestId() {
    RequestTraceContext.set("tx read/req 001");
    TransactionReadQueryStatement hot1 = TransactionReadQueryStatement.from(query(null));
    RequestTraceContext.set("tx read/req 002");
    TransactionReadQueryStatement hot2 = TransactionReadQueryStatement.from(query(null));

    TransactionArchiveReadQueryStatement archive =
        TransactionArchiveReadQueryStatement.from(query(null));
    TransactionDetailQueryStatement detail =
        TransactionDetailQueryStatement.from(
            new com.aquilabank.domain.transaction.model.TransactionDetailQuery(11L, "tr-ref-001"));
    TransactionDetailQueryStatement archiveDetail =
        TransactionDetailQueryStatement.fromArchive(
            new com.aquilabank.domain.transaction.model.TransactionDetailQuery(11L, "tr-ref-001"));

    assertThat(hot1.sql()).isSameAs(hot2.sql());
    assertThat(hot1.sql()).startsWith("/* queryShape=hot-timeline */");
    assertThat(hot1.sql()).doesNotContain("requestId=");
    assertThat(archive.sql()).startsWith("/* queryShape=archive-timeline */");
    assertThat(archive.sql()).doesNotContain("requestId=");
    assertThat(detail.sql()).startsWith("/* queryShape=detail-exact */");
    assertThat(detail.sql())
        .doesNotContain("requestId=")
        .contains("JOIN transaction_read_model trm")
        .contains("ON trm.ledger_entry_id = le.id")
        .contains("AND trm.booked_at = le.booked_at");
    assertThat(archiveDetail.sql())
        .startsWith("/* queryShape=detail-archive-exact */")
        .doesNotContain("requestId=")
        .contains("JOIN transaction_read_model_archive trm")
        .contains("ON trm.ledger_entry_id = le.id")
        .contains("AND trm.booked_at = le.booked_at");
    assertThat(archiveDetail.params().getValue("accountId")).isEqualTo(11L);
    assertThat(archiveDetail.params().getValue("transactionReference")).isEqualTo("tr-ref-001");
    assertThat(archiveDetail.params().getValue("rowLimit")).isEqualTo(2);
  }

  @Test
  void timelineQueryStatementsBindAllOptionalFiltersIdenticallyForHotAndArchive() {
    TransactionQuery filteredQuery =
        new TransactionQuery(
            11L,
            FROM,
            TO,
            25,
            new TransactionCursor(CURSOR_AT, 99L),
            com.aquilabank.domain.transaction.model.TransactionStatus.BOOKED,
            com.aquilabank.domain.transaction.model.TransactionDirection.DEBIT,
            1000L,
            5000L,
            "TRX-EXACT-1");

    TransactionReadQueryStatement hot = TransactionReadQueryStatement.from(filteredQuery);
    TransactionArchiveReadQueryStatement archive =
        TransactionArchiveReadQueryStatement.from(filteredQuery);

    assertThat(hot.sql())
        .contains("FROM transaction_read_model")
        .contains("AND transaction_status = :status")
        .contains("AND direction = :direction")
        .contains("AND amount_minor >= :minAmountMinor")
        .contains("AND amount_minor <= :maxAmountMinor")
        .contains("AND transaction_reference = :transactionReference")
        .contains("AND (booked_at, id) < (:cursorBookedAt, :cursorId)");
    assertThat(archive.sql())
        .contains("FROM transaction_read_model_archive")
        .contains("AND transaction_status = :status")
        .contains("AND direction = :direction")
        .contains("AND amount_minor >= :minAmountMinor")
        .contains("AND amount_minor <= :maxAmountMinor")
        .contains("AND transaction_reference = :transactionReference")
        .contains("AND (booked_at, id) < (:cursorBookedAt, :cursorId)");
    assertThat(hot.params().getValues()).isEqualTo(archive.params().getValues());
  }

  @Test
  void queryTraceCommentFallsBackAndSanitizesShapeToken() {
    assertThat(TransactionReadSqlTraceComment.current("   "))
        .startsWith("/* queryShape=unknown */");
    assertThat(TransactionReadSqlTraceComment.current(null)).startsWith("/* queryShape=unknown */");
    assertThat(TransactionReadSqlTraceComment.current("AZaz09_.:-"))
        .startsWith("/* queryShape=AZaz09_.:- */");
    assertThat(TransactionReadSqlTraceComment.current("x".repeat(120)))
        .startsWith("/* queryShape=%s */".formatted("x".repeat(96)));
    assertThat(TransactionReadSqlTraceComment.current("bad/shape " + "y".repeat(120)))
        .startsWith("/* queryShape=%s */".formatted("bad_shape_" + "y".repeat(86)));
  }

  @Test
  void buildsAllSixtyFourBitmaskSqlShapesDeterministically() {
    java.util.Set<String> hotSqls = new java.util.HashSet<>();
    java.util.Set<String> archiveSqls = new java.util.HashSet<>();
    for (int mask = 0; mask < 64; mask++) {
      TransactionQuery candidate =
          new TransactionQuery(
              11L,
              FROM,
              TO,
              20,
              (mask & 1) != 0 ? new TransactionCursor(CURSOR_AT, 99L) : null,
              (mask & 2) != 0
                  ? com.aquilabank.domain.transaction.model.TransactionStatus.BOOKED
                  : null,
              (mask & 4) != 0
                  ? com.aquilabank.domain.transaction.model.TransactionDirection.DEBIT
                  : null,
              (mask & 8) != 0 ? 100L : null,
              (mask & 16) != 0 ? 500L : null,
              (mask & 32) != 0 ? "TRX-1" : null);
      String hotSql = TransactionReadQueryStatement.from(candidate).sql();
      String archiveSql = TransactionArchiveReadQueryStatement.from(candidate).sql();
      assertThat(hotSql).isSameAs(TransactionReadQueryStatement.from(candidate).sql());
      assertThat(archiveSql).isSameAs(TransactionArchiveReadQueryStatement.from(candidate).sql());
      hotSqls.add(hotSql);
      archiveSqls.add(archiveSql);
    }
    assertThat(hotSqls).hasSize(64);
    assertThat(archiveSqls).hasSize(64);
  }

  @Test
  void queryTraceCommentHelperIsPackageScoped() {
    assertThat(new TransactionReadSqlTraceComment()).isNotNull();
  }

  private static TransactionQuery query(TransactionCursor cursor) {
    return new TransactionQuery(11L, FROM, TO, 50, cursor, null, null, null, null, null);
  }
}
