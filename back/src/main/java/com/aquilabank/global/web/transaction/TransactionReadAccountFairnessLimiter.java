package com.aquilabank.global.web.transaction;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 단일 hot account가 transaction read worker를 독점하지 않게 계좌별 동시 실행을 제한합니다. */
@Component
public class TransactionReadAccountFairnessLimiter {

  private final ConcurrentHashMap<Long, AtomicInteger> activeRequests = new ConcurrentHashMap<>();
  private final int maxConcurrentRequests;

  public TransactionReadAccountFairnessLimiter(
      @Value("${transaction.read.per-account.max-concurrency:2}") int maxConcurrentRequests) {
    if (maxConcurrentRequests < 1) {
      throw new IllegalArgumentException("maxConcurrentRequests must be positive");
    }
    this.maxConcurrentRequests = maxConcurrentRequests;
  }

  public <T> T execute(long accountId, Supplier<T> supplier) {
    AtomicInteger counter =
        activeRequests.computeIfAbsent(accountId, ignored -> new AtomicInteger());
    int active = counter.incrementAndGet();
    if (active > maxConcurrentRequests) {
      release(accountId, counter);
      throw new TransactionReadAccountFairnessRejectedException();
    }

    try {
      return supplier.get();
    } finally {
      release(accountId, counter);
    }
  }

  private void release(long accountId, AtomicInteger counter) {
    if (counter.decrementAndGet() == 0) {
      activeRequests.remove(accountId, counter);
    }
  }
}
