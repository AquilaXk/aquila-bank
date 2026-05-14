package com.aquilabank.global.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import com.aquilabank.support.PostgresContainerTestSupport;
import com.aquilabank.support.TransactionArchiveReadModelBaselineFixture;
import com.aquilabank.support.TransactionArchiveReadModelBaselineFixture.BaselineWindow;
import com.aquilabank.support.TransactionExplainPlan;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class JdbcTransactionArchiveReadRepositoryBaselineIntegrationTest
    extends PostgresContainerTestSupport {

  private static final Object SEED_LOCK = new Object();
  private static BaselineWindow sharedBaselineWindow;

  private final TransactionArchiveReadModelBaselineFixture fixture =
      new TransactionArchiveReadModelBaselineFixture();

  @Autowired private JdbcTransactionArchiveReadRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private PlatformTransactionManager transactionManager;

  private BaselineWindow baselineWindow;

  @BeforeEach
  void setUpDatabase() {
    if (sharedBaselineWindow != null) {
      baselineWindow = sharedBaselineWindow;
      return;
    }

    synchronized (SEED_LOCK) {
      if (sharedBaselineWindow == null) {
        resetBankingTables(jdbcTemplate);
        // archive baseline fixture는 cold table plan 검증용이라 runtime statement timeout과 분리합니다.
        commitWithStatementTimeout(
            transactionManager,
            jdbcTemplate,
            90,
            60,
            () -> sharedBaselineWindow = fixture.seed(jdbcTemplate));
      }
      baselineWindow = sharedBaselineWindow;
    }
  }

  @Test
  void firstPageUsesArchiveAccountCursorIndexWithoutSeqScanOrSort() {
    TransactionQuery query = query(null, null);

    TransactionSlice slice = repository.fetchArchived(query);
    TransactionExplainPlan plan = explain(query);

    assertThat(slice.items()).hasSize(50);
    assertThat(slice.hasNext()).isTrue();
    assertThat(plan.rootNodeType()).isEqualTo("Limit");
    assertThat(plan.actualRows()).isEqualTo(51.0d);
    assertThat(plan.usesIndex("idx_transaction_read_model_archive_account_cursor")).isTrue();
    assertThat(plan.hasNodeType("Seq Scan")).isFalse();
    assertThat(plan.hasNodeType("Sort")).isFalse();
  }

  @Test
  void cursorPageKeepsArchiveAccountCursorIndex() {
    TransactionSlice firstSlice = repository.fetchArchived(query(null, null));
    TransactionQuery nextPageQuery = query(firstSlice.nextCursor(), null);

    TransactionSlice nextSlice = repository.fetchArchived(nextPageQuery);
    TransactionExplainPlan plan = explain(nextPageQuery);

    assertThat(firstSlice.nextCursor()).isNotNull();
    assertThat(nextSlice.items()).hasSize(50);
    assertThat(nextSlice.items().getFirst().bookedAt())
        .isBeforeOrEqualTo(firstSlice.items().getLast().bookedAt());
    assertThat(plan.usesIndex("idx_transaction_read_model_archive_account_cursor")).isTrue();
    assertThat(plan.hasNodeType("Seq Scan")).isFalse();
    assertThat(plan.hasNodeType("Sort")).isFalse();
  }

  @Test
  void deepCursorPageKeepsArchiveAccountCursorIndexWithoutSeqScanOrSort() {
    TransactionCursor deepCursor =
        new TransactionCursor(baselineWindow.from().plus(Duration.ofDays(14)), Long.MAX_VALUE);
    TransactionQuery query = query(deepCursor, null);

    TransactionSlice slice = repository.fetchArchived(query);
    TransactionExplainPlan plan = explain(query);

    assertThat(slice.items()).hasSize(50);
    assertThat(slice.items().getFirst().bookedAt()).isBeforeOrEqualTo(deepCursor.bookedAt());
    assertThat(plan.rootNodeType()).isEqualTo("Limit");
    assertThat(plan.actualRows()).isEqualTo(51.0d);
    assertThat(plan.usesIndex("idx_transaction_read_model_archive_account_cursor")).isTrue();
    assertThat(plan.hasNodeType("Seq Scan")).isFalse();
    assertThat(plan.hasNodeType("Sort")).isFalse();
  }

  @Test
  void statusFilteredCursorPageUsesArchiveStatusCursorIndex() {
    TransactionSlice firstSlice = repository.fetchArchived(query(null, TransactionStatus.BOOKED));
    TransactionQuery nextPageQuery = query(firstSlice.nextCursor(), TransactionStatus.BOOKED);

    TransactionSlice nextSlice = repository.fetchArchived(nextPageQuery);
    TransactionExplainPlan plan = explain(nextPageQuery);

    assertThat(firstSlice.nextCursor()).isNotNull();
    assertThat(nextSlice.items()).hasSize(50);
    assertThat(nextSlice.items()).allMatch(item -> item.status() == TransactionStatus.BOOKED);
    assertThat(plan.usesIndex("idx_transaction_read_model_archive_account_status_cursor")).isTrue();
    assertThat(plan.hasNodeType("Seq Scan")).isFalse();
    assertThat(plan.hasNodeType("Sort")).isFalse();
  }

  @Test
  void transactionReferenceExactLookupUsesArchiveReferenceCursorIndex() {
    TransactionQuery query = query(null, null, "archive-hot-account-trx-40000");

    TransactionSlice slice = repository.fetchArchived(query);
    TransactionExplainPlan plan = explain(query);

    assertThat(slice.items()).hasSize(1);
    assertThat(slice.items().getFirst().transactionReference())
        .isEqualTo("archive-hot-account-trx-40000");
    assertThat(plan.usesIndex("idx_transaction_read_model_archive_account_reference_cursor"))
        .isTrue();
    assertThat(plan.hasNodeType("Seq Scan")).isFalse();
    assertThat(plan.hasNodeType("Sort")).isFalse();
  }

  private TransactionQuery query(
      com.aquilabank.domain.transaction.model.TransactionCursor cursor, TransactionStatus status) {
    return query(cursor, status, null);
  }

  private TransactionQuery query(
      com.aquilabank.domain.transaction.model.TransactionCursor cursor,
      TransactionStatus status,
      String transactionReference) {
    return new TransactionQuery(
        baselineWindow.hotAccountId(),
        baselineWindow.from(),
        baselineWindow.to(),
        50,
        cursor,
        status,
        null,
        null,
        null,
        transactionReference);
  }

  private TransactionExplainPlan explain(TransactionQuery query) {
    TransactionArchiveReadQueryStatement statement =
        TransactionArchiveReadQueryStatement.from(query);
    String explainJson =
        jdbcTemplate.queryForObject(
            "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)\n" + statement.sql(),
            statement.params(),
            (rs, rowNum) -> rs.getString(1));
    if (explainJson == null) {
      throw new IllegalStateException("EXPLAIN did not return JSON");
    }
    return TransactionExplainPlan.fromJson(objectMapper, explainJson);
  }
}
