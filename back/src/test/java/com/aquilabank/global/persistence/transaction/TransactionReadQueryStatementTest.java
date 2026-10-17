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
  void queryStatementsIncludeSanitizedRequestIdTraceComment() {
    RequestTraceContext.set("tx read/req 001");

    TransactionReadQueryStatement hot = TransactionReadQueryStatement.from(query(null));
    TransactionArchiveReadQueryStatement archive =
        TransactionArchiveReadQueryStatement.from(query(null));
    TransactionDetailQueryStatement detail =
        TransactionDetailQueryStatement.from(
            new com.aquilabank.domain.transaction.model.TransactionDetailQuery(11L, "tr-ref-001"));
    TransactionDetailQueryStatement archiveDetail =
        TransactionDetailQueryStatement.fromArchive(
            new com.aquilabank.domain.transaction.model.TransactionDetailQuery(11L, "tr-ref-001"));

    assertThat(hot.sql()).startsWith("/* requestId=tx_read_req_001 queryShape=hot-timeline */");
    assertThat(archive.sql())
        .startsWith("/* requestId=tx_read_req_001 queryShape=archive-timeline */");
    assertThat(detail.sql()).startsWith("/* requestId=tx_read_req_001 queryShape=detail-exact */");
    assertThat(detail.sql())
        .contains("JOIN transaction_read_model trm")
        .contains("ON trm.ledger_entry_id = le.id")
        .contains("AND trm.booked_at = le.booked_at");
    assertThat(archiveDetail.sql())
        .startsWith("/* requestId=tx_read_req_001 queryShape=detail-archive-exact */")
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
  void queryTraceCommentFallsBackAndBoundsRequestId() {
    RequestTraceContext.set("   ");
    assertThat(TransactionReadQueryStatement.from(query(null)).sql())
        .startsWith("/* requestId=unknown queryShape=hot-timeline */");

    RequestTraceContext.set("AZaz09_.:-");
    assertThat(TransactionReadQueryStatement.from(query(null)).sql())
        .startsWith("/* requestId=AZaz09_.:- queryShape=hot-timeline */");

    RequestTraceContext.set("x".repeat(120));
    assertThat(TransactionReadQueryStatement.from(query(null)).sql())
        .startsWith("/* requestId=%s queryShape=hot-timeline */".formatted("x".repeat(96)));

    RequestTraceContext.set(("bad/id " + "y".repeat(120)));
    assertThat(TransactionReadQueryStatement.from(query(null)).sql())
        .startsWith(
            "/* requestId=%s queryShape=hot-timeline */".formatted("bad_id_" + "y".repeat(89)));
  }

  @Test
  void queryTraceCommentHelperIsPackageScoped() {
    assertThat(new TransactionReadSqlTraceComment()).isNotNull();
  }

  private static TransactionQuery query(TransactionCursor cursor) {
    return new TransactionQuery(11L, FROM, TO, 50, cursor, null, null, null, null, null);
  }
}
