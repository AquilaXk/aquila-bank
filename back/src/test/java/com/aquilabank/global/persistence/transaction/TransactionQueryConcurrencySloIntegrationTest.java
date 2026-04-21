package com.aquilabank.global.persistence.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.domain.transaction.model.TransactionDirection;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import com.aquilabank.support.PostgresContainerTestSupport;
import com.aquilabank.support.TransactionReadModelBaselineFixture;
import com.aquilabank.support.TransactionReadModelBaselineFixture.BaselineWindow;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = {"spring.flyway.enabled=true", "management.health.db.enabled=true"})
class TransactionQueryConcurrencySloIntegrationTest extends PostgresContainerTestSupport {

  private static final int REQUEST_COUNT = 16;
  private static final int CONCURRENCY = 16;
  private static final int PAGE_SIZE = 50;
  private static final long P95_SLO_MILLIS = 350;
  private static final long MAX_SLO_MILLIS = 750;
  private static final Duration RESULT_TIMEOUT = Duration.ofSeconds(20);

  private static final Object SEED_LOCK = new Object();
  private static BaselineWindow sharedBaselineWindow;

  private final TransactionReadModelBaselineFixture fixture =
      new TransactionReadModelBaselineFixture();

  @Autowired private JdbcTransactionReadRepository repository;

  @Autowired private NamedParameterJdbcTemplate jdbcTemplate;

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
        // 동시성 검증은 baseline fixture를 재사용해 query shape별 데이터 분포를 고정합니다.
        commit(transactionManager, 30, () -> sharedBaselineWindow = fixture.seed(jdbcTemplate));
      }
      baselineWindow = sharedBaselineWindow;
    }
  }

  @Test
  @Timeout(30)
  void mixedTransactionQueriesStayWithinConcurrencySlo() throws Exception {
    TransactionCursor cursor = repository.fetch(firstPageQuery()).nextCursor();
    assertThat(cursor).isNotNull();

    ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
    CountDownLatch startSignal = new CountDownLatch(1);
    try {
      List<Future<QueryResult>> futures = new ArrayList<>();
      for (int index = 0; index < REQUEST_COUNT; index++) {
        int requestIndex = index;
        futures.add(executor.submit(() -> executeTimedQuery(requestIndex, cursor, startSignal)));
      }

      startSignal.countDown();

      List<QueryResult> results = new ArrayList<>();
      for (Future<QueryResult> future : futures) {
        results.add(future.get(RESULT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
      }

      List<String> failures =
          results.stream().filter(result -> !result.success()).map(QueryResult::message).toList();
      assertThat(failures).isEmpty();
      assertThat(results).allMatch(result -> result.itemCount() > 0);

      List<Long> latencies = results.stream().map(QueryResult::latencyMillis).sorted().toList();
      long p95 = percentile(latencies, 0.95d);
      long max = latencies.getLast();

      assertThat(p95).isLessThanOrEqualTo(P95_SLO_MILLIS);
      assertThat(max).isLessThanOrEqualTo(MAX_SLO_MILLIS);
    } finally {
      executor.shutdownNow();
    }
  }

  private QueryResult executeTimedQuery(
      int requestIndex, TransactionCursor cursor, CountDownLatch startSignal) {
    try {
      startSignal.await();
      long startedAt = System.nanoTime();
      TransactionSlice slice = repository.fetch(queryFor(requestIndex, cursor));
      long latencyMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
      return QueryResult.success(latencyMillis, slice.items().size());
    } catch (Exception ex) {
      return QueryResult.failure(ex);
    }
  }

  private TransactionQuery queryFor(int requestIndex, TransactionCursor cursor) {
    return switch (requestIndex % 6) {
      case 0 -> firstPageQuery();
      case 1 -> cursorPageQuery(cursor);
      case 2 -> statusQuery();
      case 3 -> directionQuery();
      case 4 -> amountQuery();
      default -> referenceQuery(10_000 + requestIndex);
    };
  }

  private TransactionQuery firstPageQuery() {
    return new TransactionQuery(
        baselineWindow.hotAccountId(),
        baselineWindow.from(),
        baselineWindow.to(),
        PAGE_SIZE,
        null,
        null,
        null,
        null,
        null,
        null);
  }

  private TransactionQuery cursorPageQuery(TransactionCursor cursor) {
    return new TransactionQuery(
        baselineWindow.hotAccountId(),
        baselineWindow.from(),
        baselineWindow.to(),
        PAGE_SIZE,
        cursor,
        null,
        null,
        null,
        null,
        null);
  }

  private TransactionQuery statusQuery() {
    return new TransactionQuery(
        baselineWindow.hotAccountId(),
        baselineWindow.from(),
        baselineWindow.to(),
        PAGE_SIZE,
        null,
        TransactionStatus.BOOKED,
        null,
        null,
        null,
        null);
  }

  private TransactionQuery directionQuery() {
    return new TransactionQuery(
        baselineWindow.hotAccountId(),
        baselineWindow.from(),
        baselineWindow.to(),
        PAGE_SIZE,
        null,
        null,
        TransactionDirection.DEBIT,
        null,
        null,
        null);
  }

  private TransactionQuery amountQuery() {
    return new TransactionQuery(
        baselineWindow.hotAccountId(),
        baselineWindow.from(),
        baselineWindow.to(),
        PAGE_SIZE,
        null,
        null,
        null,
        1700L,
        1700L,
        null);
  }

  private TransactionQuery referenceQuery(int sequence) {
    return new TransactionQuery(
        baselineWindow.hotAccountId(),
        baselineWindow.from(),
        baselineWindow.to(),
        PAGE_SIZE,
        null,
        null,
        null,
        null,
        null,
        "hot-account-trx-" + sequence);
  }

  private static long percentile(List<Long> sortedItems, double percentile) {
    assertThat(sortedItems).isSortedAccordingTo(Comparator.naturalOrder());
    int index = (int) Math.floor((sortedItems.size() - 1) * percentile);
    return sortedItems.get(index);
  }

  private record QueryResult(boolean success, long latencyMillis, int itemCount, String message) {

    private static QueryResult success(long latencyMillis, int itemCount) {
      return new QueryResult(true, latencyMillis, itemCount, "");
    }

    private static QueryResult failure(Exception ex) {
      return new QueryResult(false, 0L, 0, ex.getClass().getSimpleName() + ": " + ex.getMessage());
    }
  }
}
