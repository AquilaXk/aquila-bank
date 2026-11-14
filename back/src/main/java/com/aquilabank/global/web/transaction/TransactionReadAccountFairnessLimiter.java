package com.aquilabank.global.web.transaction;

import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 단일 hot account가 transaction read worker를 독점하지 않게 고정 슬롯 CAS 테이블로 동시 실행을 제한합니다. */
@Component
public class TransactionReadAccountFairnessLimiter {

  private static final int SLOT_COUNT = 4096;
  private static final int SLOT_MASK = SLOT_COUNT - 1;

  private final AtomicIntegerArray activeSlotCounts = new AtomicIntegerArray(SLOT_COUNT);
  private final int maxConcurrentRequests;

  public TransactionReadAccountFairnessLimiter(
      @Value("${transaction.read.per-account.max-concurrency:2}") int maxConcurrentRequests) {
    if (maxConcurrentRequests < 1) {
      throw new IllegalArgumentException("maxConcurrentRequests must be positive");
    }
    this.maxConcurrentRequests = maxConcurrentRequests;
  }

  public <T> T execute(long accountId, Supplier<T> supplier) {
    int slot = acquire(accountId);
    try {
      return supplier.get();
    } finally {
      release(slot);
    }
  }

  public int acquire(long accountId) {
    int slot = slotFor(accountId);
    int current;
    do {
      current = activeSlotCounts.get(slot);
      if (current >= maxConcurrentRequests) {
        throw new TransactionReadAccountFairnessRejectedException();
      }
    } while (!activeSlotCounts.compareAndSet(slot, current, current + 1));
    return slot;
  }

  public void release(int slot) {
    activeSlotCounts.decrementAndGet(slot);
  }

  private static int slotFor(long accountId) {
    // 64비트 SplitMix 확산으로 연속된 account_id 가 같은 슬롯에 몰리는 충돌을 줄인다.
    long mixed = accountId * 0x9e3779b97f4a7c15L;
    mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
    mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
    return (int) ((mixed ^ (mixed >>> 31)) & SLOT_MASK);
  }
}
