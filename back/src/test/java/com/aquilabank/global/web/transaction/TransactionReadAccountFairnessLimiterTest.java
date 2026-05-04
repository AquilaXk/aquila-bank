package com.aquilabank.global.web.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

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
          .isInstanceOf(TransactionReadAccountFairnessRejectedException.class)
          .hasMessage("transaction read account concurrency limit exceeded");

      releaseOwner.countDown();
      assertThat(owner.get(1, TimeUnit.SECONDS)).isEqualTo("owner");
    } finally {
      releaseOwner.countDown();
      executor.shutdownNow();
    }
  }

  @Test
  void allowsThreeConcurrentRequestsPerAccountForOciBurstShape() throws Exception {
    TransactionReadAccountFairnessLimiter limiter = new TransactionReadAccountFairnessLimiter(3);
    ExecutorService executor = Executors.newFixedThreadPool(3);
    CountDownLatch ownersStarted = new CountDownLatch(3);
    CountDownLatch releaseOwners = new CountDownLatch(1);
    try {
      Future<String> first = submitOwner(executor, limiter, ownersStarted, releaseOwners, "first");
      Future<String> second =
          submitOwner(executor, limiter, ownersStarted, releaseOwners, "second");
      Future<String> third = submitOwner(executor, limiter, ownersStarted, releaseOwners, "third");
      assertThat(ownersStarted.await(1, TimeUnit.SECONDS)).isTrue();

      assertThatThrownBy(() -> limiter.execute(101L, () -> "rejected"))
          .isInstanceOf(TransactionReadAccountFairnessRejectedException.class)
          .hasMessage("transaction read account concurrency limit exceeded");

      releaseOwners.countDown();
      assertThat(first.get(1, TimeUnit.SECONDS)).isEqualTo("first");
      assertThat(second.get(1, TimeUnit.SECONDS)).isEqualTo("second");
      assertThat(third.get(1, TimeUnit.SECONDS)).isEqualTo("third");
    } finally {
      releaseOwners.countDown();
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

  private static Future<String> submitOwner(
      ExecutorService executor,
      TransactionReadAccountFairnessLimiter limiter,
      CountDownLatch ownersStarted,
      CountDownLatch releaseOwners,
      String result) {
    return executor.submit(
        () ->
            limiter.execute(
                101L,
                () -> {
                  ownersStarted.countDown();
                  await(releaseOwners);
                  return result;
                }));
  }
}
