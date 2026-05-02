package com.aquilabank.global.web.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TransactionReadSingleFlightTest {

  private final TransactionReadSingleFlight singleFlight = new TransactionReadSingleFlight();
  private final TransactionQuery query =
      new TransactionQuery(
          101L,
          Instant.parse("2026-04-01T00:00:00Z"),
          Instant.parse("2026-04-17T00:00:00Z"),
          50,
          null,
          null,
          null,
          null,
          null,
          null);

  @Test
  void coalescesIdenticalInFlightRequest() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch ownerStarted = new CountDownLatch(1);
    CountDownLatch releaseOwner = new CountDownLatch(1);
    AtomicInteger supplierCalls = new AtomicInteger();
    AtomicReference<Thread> waiterThread = new AtomicReference<>();
    TransactionSlice expected = new TransactionSlice(List.of(), null, false, 50);
    try {
      Future<TransactionSlice> first =
          executor.submit(
              () ->
                  singleFlight.get(
                      "active",
                      query,
                      () -> {
                        supplierCalls.incrementAndGet();
                        ownerStarted.countDown();
                        await(releaseOwner);
                        return expected;
                      }));
      assertThat(ownerStarted.await(1, TimeUnit.SECONDS)).isTrue();

      Future<TransactionSlice> second =
          executor.submit(
              () -> {
                waiterThread.set(Thread.currentThread());
                return singleFlight.get(
                    "active",
                    query,
                    () -> {
                      supplierCalls.incrementAndGet();
                      return new TransactionSlice(List.of(), null, false, 50);
                    });
              });
      assertThat(awaitWaiting(waiterThread)).isTrue();

      releaseOwner.countDown();

      assertThat(first.get(1, TimeUnit.SECONDS)).isSameAs(expected);
      assertThat(second.get(1, TimeUnit.SECONDS)).isSameAs(expected);
      assertThat(supplierCalls).hasValue(1);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void propagatesOwnerFailureToWaiterAndAllowsRetry() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch ownerStarted = new CountDownLatch(1);
    CountDownLatch releaseOwner = new CountDownLatch(1);
    AtomicInteger supplierCalls = new AtomicInteger();
    AtomicReference<Thread> waiterThread = new AtomicReference<>();
    try {
      Future<TransactionSlice> first =
          executor.submit(
              () ->
                  singleFlight.get(
                      "active",
                      query,
                      () -> {
                        supplierCalls.incrementAndGet();
                        ownerStarted.countDown();
                        await(releaseOwner);
                        throw new IllegalStateException("query failed");
                      }));
      assertThat(ownerStarted.await(1, TimeUnit.SECONDS)).isTrue();

      Future<TransactionSlice> second =
          executor.submit(
              () -> {
                waiterThread.set(Thread.currentThread());
                return singleFlight.get(
                    "active",
                    query,
                    () -> {
                      supplierCalls.incrementAndGet();
                      return new TransactionSlice(List.of(), null, false, 50);
                    });
              });
      assertThat(awaitWaiting(waiterThread)).isTrue();

      releaseOwner.countDown();

      assertThatThrownBy(() -> first.get(1, TimeUnit.SECONDS))
          .hasRootCauseInstanceOf(IllegalStateException.class);
      assertThatThrownBy(() -> second.get(1, TimeUnit.SECONDS))
          .hasRootCauseInstanceOf(IllegalStateException.class);
      TransactionSlice recovered =
          singleFlight.get(
              "active",
              query,
              () -> {
                supplierCalls.incrementAndGet();
                return new TransactionSlice(List.of(), null, false, 50);
              });

      assertThat(recovered.limit()).isEqualTo(50);
      assertThat(supplierCalls).hasValue(2);
    } finally {
      executor.shutdownNow();
    }
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

  private static boolean awaitWaiting(AtomicReference<Thread> threadRef) {
    long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
    while (System.nanoTime() < deadlineNanos) {
      Thread thread = threadRef.get();
      if (thread != null
          && (thread.getState() == Thread.State.WAITING
              || thread.getState() == Thread.State.TIMED_WAITING)) {
        return true;
      }
      Thread.yield();
    }
    return false;
  }
}
