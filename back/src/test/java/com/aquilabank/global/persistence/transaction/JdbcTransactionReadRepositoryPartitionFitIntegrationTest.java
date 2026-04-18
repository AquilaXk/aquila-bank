package com.aquilabank.global.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import com.aquilabank.support.PostgresContainerTestSupport;
import com.aquilabank.support.TransactionExplainPlan;
import com.aquilabank.support.TransactionReadModelPartitionFitFixture;
import com.aquilabank.support.TransactionReadModelPartitionFitFixture.PartitionFitWindow;
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
class JdbcTransactionReadRepositoryPartitionFitIntegrationTest
    extends PostgresContainerTestSupport {

  private static final Object SEED_LOCK = new Object();

  private static PartitionFitWindow sharedWindow;

  private final TransactionReadModelPartitionFitFixture fixture =
      new TransactionReadModelPartitionFitFixture();

  @Autowired private JdbcTransactionReadRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private PlatformTransactionManager transactionManager;

  private PartitionFitWindow partitionFitWindow;

  @BeforeEach
  void setUpDatabase() {
    if (sharedWindow != null) {
      partitionFitWindow = sharedWindow;
      return;
    }

    synchronized (SEED_LOCK) {
      if (sharedWindow == null) {
        resetBankingTables(jdbcTemplate);
        // 1년치 히스토리 적재는 baseline보다 크므로 준비 단계 timeout을 별도로 늘립니다.
        commit(transactionManager, 60, () -> sharedWindow = fixture.seed(jdbcTemplate));
      }
      partitionFitWindow = sharedWindow;
    }
  }

  @Test
  void recentWindowKeepsAccountCursorIndexEvenWithLongHistory() {
    TransactionQuery query =
        new TransactionQuery(
            partitionFitWindow.hotAccountId(),
            partitionFitWindow.recentFrom(),
            partitionFitWindow.recentTo(),
            50,
            null,
            null,
            null,
            null,
            null,
            null);

    TransactionSlice slice = repository.fetch(query);
    TransactionExplainPlan plan = explain(query);

    assertThat(slice.items()).hasSize(50);
    assertThat(slice.hasNext()).isTrue();
    assertThat(plan.usesIndex("idx_transaction_read_model_account_cursor")).isTrue();
    assertThat(plan.hasNodeType("Seq Scan")).isFalse();
    assertThat(plan.hasNodeType("Sort")).isFalse();
  }

  @Test
  void historicalWindowStillUsesAccountCursorIndexWithoutSeqScan() {
    TransactionQuery query =
        new TransactionQuery(
            partitionFitWindow.hotAccountId(),
            partitionFitWindow.historicalFrom(),
            partitionFitWindow.historicalTo(),
            50,
            null,
            null,
            null,
            null,
            null,
            null);

    TransactionSlice slice = repository.fetch(query);
    TransactionExplainPlan plan = explain(query);

    assertThat(slice.items()).hasSize(50);
    assertThat(slice.hasNext()).isTrue();
    assertThat(plan.usesIndex("idx_transaction_read_model_account_cursor")).isTrue();
    assertThat(plan.hasNodeType("Seq Scan")).isFalse();
    assertThat(plan.hasNodeType("Sort")).isFalse();
  }

  @Test
  void historicalStatusFilterKeepsStatusCursorIndexWithoutSeqScan() {
    TransactionQuery query =
        new TransactionQuery(
            partitionFitWindow.hotAccountId(),
            partitionFitWindow.historicalFrom(),
            partitionFitWindow.historicalTo(),
            50,
            null,
            TransactionStatus.BOOKED,
            null,
            null,
            null,
            null);

    TransactionSlice slice = repository.fetch(query);
    TransactionExplainPlan plan = explain(query);

    assertThat(slice.items()).hasSize(50);
    assertThat(slice.hasNext()).isTrue();
    assertThat(slice.items()).allMatch(item -> item.status() == TransactionStatus.BOOKED);
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
