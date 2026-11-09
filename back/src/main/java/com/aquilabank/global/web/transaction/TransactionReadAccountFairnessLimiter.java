package com.aquilabank.global.web.transaction;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 단일 hot account가 transaction read worker를 독점하지 않게 계좌별 동시 실행을 제한합니다. */
@Component
public class TransactionReadAccountFairnessLimiter {

  private final ConcurrentHashMap<Long, Integer> activeRequests = new ConcurrentHashMap<>();
  private final int maxConcurrentRequests;

  public TransactionReadAccountFairnessLimiter(
      @Value("${transaction.read.per-account.max-concurrency:2}") int maxConcurrentRequests) {
    if (maxConcurrentRequests < 1) {
      throw new IllegalArgumentException("maxConcurrentRequests must be positive");
    }
    this.maxConcurrentRequests = maxConcurrentRequests;
  }

  public <T> T execute(long accountId, Supplier<T> supplier) {
    int active = activeRequests.merge(accountId, 1, Integer::sum);
    if (active > maxConcurrentRequests) {
      release(accountId);
      throw new TransactionReadAccountFairnessRejectedException();
    }

    try {
      return supplier.get();
    } finally {
      release(accountId);
    }
  }

  private void release(long accountId) {
    activeRequests.computeIfPresent(accountId, (ignored, count) -> count > 1 ? count - 1 : null);
  }
}
