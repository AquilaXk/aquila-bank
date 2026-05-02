package com.aquilabank.global.web.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class TransactionReadAccountFairnessLimiterTest {

  @Test
  void rejectsConcurrentRequestAbovePerAccountLimit() throws Exception {
    TransactionReadAccountFairnessLimiter limiter = new TransactionReadAccountFairnessLimiter(1);
    ExecutorService executor = Executors.newSingleThreadExecutor();
    CountDownLatch ownerStarted = new CountDownLatch(1);
    CountDownLatch releaseOwner = new CountDownLatch(1);
    try {
      Future<String> owner =
          executor.submit(
              () ->
                  limiter.execute(
                      101L,
                      () -> {
                        ownerStarted.countDown();
                        await(releaseOwner);
                        return "owner";
                      }));
      assertThat(ownerStarted.await(1, TimeUnit.SECONDS)).isTrue();

      assertThatThrownBy(() -> limiter.execute(101L, () -> "rejected"))
          .isInstanceOf(ResponseStatusException.class)
          .hasMessageContaining("429 TOO_MANY_REQUESTS");

      releaseOwner.countDown();
      assertThat(owner.get(1, TimeUnit.SECONDS)).isEqualTo("owner");
    } finally {
      releaseOwner.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void separatesDifferentAccountsAndReleasesAfterFailure() {
    TransactionReadAccountFairnessLimiter limiter = new TransactionReadAccountFairnessLimiter(1);

    assertThat(limiter.execute(101L, () -> "first")).isEqualTo("first");
    assertThat(limiter.execute(202L, () -> "second")).isEqualTo("second");
    assertThatThrownBy(
            () ->
                limiter.execute(
                    101L,
                    () -> {
                      throw new IllegalStateException("failed");
                    }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(limiter.execute(101L, () -> "recovered")).isEqualTo("recovered");
  }

  @Test
  void rejectsInvalidLimitConfiguration() {
    assertThatThrownBy(() -> new TransactionReadAccountFairnessLimiter(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("maxConcurrentRequests must be positive");
  }

  private static void await(CountDownLatch latch) {
    try {
      if (!latch.await(1, TimeUnit.SECONDS)) {
        throw new IllegalStateException("latch timeout");
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(ex);
    }
  }
}
