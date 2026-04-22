package com.aquilabank.global.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.support.PostgresContainerTestSupport;
import com.aquilabank.support.TransactionExplainPlan;
import com.aquilabank.support.TransactionReadModelRetentionCutoffLoadFixture;
import com.aquilabank.support.TransactionReadModelRetentionCutoffLoadFixture.CutoffLoadWindow;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class JdbcTransactionReadModelRetentionCutoffLoadIntegrationTest
    extends PostgresContainerTestSupport {

  private static final Object SEED_LOCK = new Object();
  private static CutoffLoadWindow sharedWindow;

  private final TransactionReadModelRetentionCutoffLoadFixture fixture =
      new TransactionReadModelRetentionCutoffLoadFixture();

  @Autowired private JdbcTransactionReadRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

  @Autowired private ObjectMapper objectMapper;

  @Autowired private PlatformTransactionManager transactionManager;

  private CutoffLoadWindow window;

  @BeforeEach
  void setUpDatabase() {
    if (sharedWindow != null) {
      window = sharedWindow;
      return;
    }

    synchronized (SEED_LOCK) {
      if (sharedWindow == null) {
        resetBankingTables(jdbcTemplate);
        // cutoff load fixture는 retention 기간별 hot/cold 분포 비교용이라 setup timeout을 늘립니다.
        commit(transactionManager, 45, () -> sharedWindow = fixture.seed(jdbcTemplate));
      }
      window = sharedWindow;
    }
  }

  @Test
  void cutoffWindowsExposeHotTableAndCleanupCandidateCost() {
    CutoffMetrics shortRetention = metrics(window.shortRetentionCutoff());
    CutoffMetrics defaultRetention = metrics(window.defaultRetentionCutoff());
    CutoffMetrics longRetention = metrics(window.longRetentionCutoff());

    assertThat(shortRetention.hotCount()).isEqualTo(3_000L);
    assertThat(shortRetention.candidateCount()).isEqualTo(69_000L);
    assertThat(defaultRetention.hotCount()).isEqualTo(36_500L);
    assertThat(defaultRetention.candidateCount()).isEqualTo(35_500L);
    assertThat(longRetention.hotCount()).isEqualTo(72_000L);
    assertThat(longRetention.candidateCount()).isZero();
  }

  @Test
  void boundedReadWindowsKeepAccountKeysetIndexAcrossCutoffs() {
    for (Instant cutoff :
        List.of(
            window.shortRetentionCutoff(),
            window.defaultRetentionCutoff(),
            window.longRetentionCutoff())) {
      TransactionQuery query = query(cutoff);

      TransactionSlice slice = repository.fetch(query);
      TransactionExplainPlan plan = explain(query);

      assertThat(slice.items()).hasSize(50);
      assertThat(slice.hasNext()).isTrue();
      assertThat(plan.usesIndex("idx_transaction_read_model_account_cursor")).isTrue();
      assertThat(plan.hasNodeType("Seq Scan")).isFalse();
      assertThat(plan.hasNodeType("Sort")).isFalse();
    }
  }

  private CutoffMetrics metrics(Instant cutoff) {
    CutoffMetrics metrics =
        jdbcTemplate.queryForObject(
            """
            SELECT COUNT(*) FILTER (WHERE booked_at >= :cutoff) AS hot_count,
                   COUNT(*) FILTER (WHERE booked_at < :cutoff) AS candidate_count
            FROM transaction_read_model
            WHERE account_id = :accountId
            """,
            new MapSqlParameterSource()
                .addValue("accountId", window.hotAccountId())
                .addValue("cutoff", Timestamp.from(cutoff)),
            (rs, rowNum) ->
                new CutoffMetrics(rs.getLong("hot_count"), rs.getLong("candidate_count")));
    if (metrics == null) {
      throw new IllegalStateException("cutoff metrics query returned no row");
    }
    return metrics;
  }

  private TransactionQuery query(Instant cutoff) {
    Instant minimumFrom = window.to().minus(Duration.ofDays(31));
    Instant from = cutoff.isAfter(minimumFrom) ? cutoff : minimumFrom;
    return new TransactionQuery(
        window.hotAccountId(), from, window.to(), 50, null, null, null, null, null, null);
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

  private record CutoffMetrics(long hotCount, long candidateCount) {}
}
