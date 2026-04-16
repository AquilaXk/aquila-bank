package com.aquilabank.global.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import com.aquilabank.support.PostgresContainerTestSupport;
import com.aquilabank.support.TransactionExplainPlan;
import com.aquilabank.support.TransactionReadModelBaselineFixture;
import com.aquilabank.support.TransactionReadModelBaselineFixture.BaselineWindow;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class JdbcTransactionReadRepositoryBaselineIntegrationTest extends PostgresContainerTestSupport {

  private final TransactionReadModelBaselineFixture fixture =
      new TransactionReadModelBaselineFixture();

  @Autowired private JdbcTransactionReadRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private PlatformTransactionManager transactionManager;

  private static final Object SEED_LOCK = new Object();
  private static BaselineWindow sharedBaselineWindow;

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
        commit(transactionManager, () -> sharedBaselineWindow = fixture.seed(jdbcTemplate));
      }
      baselineWindow = sharedBaselineWindow;
    }
  }

  @Test
  void firstPageUsesAccountCursorIndexWithoutSeqScanOrSort() {
    TransactionQuery query =
        new TransactionQuery(
            baselineWindow.hotAccountId(),
            baselineWindow.from(),
            baselineWindow.to(),
            50,
            null,
            null);

    TransactionSlice slice = repository.fetch(query);
    TransactionExplainPlan plan = explain(query);

    assertThat(slice.items()).hasSize(50);
    assertThat(slice.hasNext()).isTrue();
    assertThat(plan.rootNodeType()).isEqualTo("Limit");
    assertThat(plan.actualRows()).isEqualTo(51.0d);
    assertThat(plan.usesIndex("idx_transaction_read_model_account_cursor")).isTrue();
    assertThat(plan.hasNodeType("Seq Scan")).isFalse();
    assertThat(plan.hasNodeType("Sort")).isFalse();
  }

  @Test
  void cursorPageKeepsSameAccountCursorIndex() {
    TransactionQuery firstPageQuery =
        new TransactionQuery(
            baselineWindow.hotAccountId(),
            baselineWindow.from(),
            baselineWindow.to(),
            50,
            null,
            null);
    TransactionSlice firstSlice = repository.fetch(firstPageQuery);

    TransactionQuery nextPageQuery =
        new TransactionQuery(
            baselineWindow.hotAccountId(),
            baselineWindow.from(),
            baselineWindow.to(),
            50,
            firstSlice.nextCursor(),
            null);

    TransactionSlice nextSlice = repository.fetch(nextPageQuery);
    TransactionExplainPlan plan = explain(nextPageQuery);

    assertThat(firstSlice.nextCursor()).isNotNull();
    assertThat(nextSlice.items()).hasSize(50);
    assertThat(nextSlice.items().getFirst().bookedAt())
        .isBeforeOrEqualTo(firstSlice.items().getLast().bookedAt());
    assertThat(plan.usesIndex("idx_transaction_read_model_account_cursor")).isTrue();
    assertThat(plan.hasNodeType("Seq Scan")).isFalse();
    assertThat(plan.hasNodeType("Sort")).isFalse();
  }

  @Test
  void statusFilteredCursorPageUsesStatusCursorIndex() {
    TransactionQuery firstPageQuery =
        new TransactionQuery(
            baselineWindow.hotAccountId(),
            baselineWindow.from(),
            baselineWindow.to(),
            50,
            null,
            TransactionStatus.BOOKED);
    TransactionSlice firstSlice = repository.fetch(firstPageQuery);

    TransactionQuery nextPageQuery =
        new TransactionQuery(
            baselineWindow.hotAccountId(),
            baselineWindow.from(),
            baselineWindow.to(),
            50,
            firstSlice.nextCursor(),
            TransactionStatus.BOOKED);

    TransactionSlice nextSlice = repository.fetch(nextPageQuery);
    TransactionExplainPlan plan = explain(nextPageQuery);

    assertThat(firstSlice.nextCursor()).isNotNull();
    assertThat(nextSlice.items()).hasSize(50);
    assertThat(nextSlice.items()).allMatch(item -> item.status() == TransactionStatus.BOOKED);
    assertThat(plan.usesIndex("idx_transaction_read_model_account_status_cursor")).isTrue();
    assertThat(plan.hasNodeType("Seq Scan")).isFalse();
    assertThat(plan.hasNodeType("Sort")).isFalse();
  }

  private TransactionExplainPlan explain(TransactionQuery query) {
    TransactionReadQueryStatement statement = TransactionReadQueryStatement.from(query);
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
