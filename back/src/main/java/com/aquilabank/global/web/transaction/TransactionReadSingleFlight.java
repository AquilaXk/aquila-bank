package com.aquilabank.global.web.transaction;

import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** 동일 transaction read in-flight 요청을 하나의 upstream 실행으로 합칩니다. */
@Component
public class TransactionReadSingleFlight {

  private final ConcurrentHashMap<RequestKey, CompletableFuture<?>> inFlight =
      new ConcurrentHashMap<>();

  public TransactionSlice get(
      String endpoint, TransactionQuery query, Supplier<TransactionSlice> supplier) {
    return execute(endpoint, query, supplier);
  }

  @SuppressWarnings("unchecked")
  public <T> T execute(String endpoint, Object queryKey, Supplier<T> supplier) {
    RequestKey key = new RequestKey(endpoint, queryKey);
    boolean[] isOwner = {false};
    CompletableFuture<T> future =
        (CompletableFuture<T>)
            inFlight.computeIfAbsent(
                key,
                ignored -> {
                  isOwner[0] = true;
                  return new CompletableFuture<T>();
                });
    if (!isOwner[0]) {
      return await(future);
    }

    try {
      T result = supplier.get();
      future.complete(result);
      return result;
    } catch (Throwable throwable) {
      future.completeExceptionally(throwable);
      throw rethrow(throwable);
    } finally {
      inFlight.remove(key, future);
    }
  }

  private <T> T await(CompletableFuture<T> future) {
    try {
      return future.join();
    } catch (CompletionException ex) {
      throw rethrow(ex.getCause());
    }
  }

  private RuntimeException rethrow(Throwable throwable) {
    if (throwable instanceof Error error) {
      throw error;
    }
    return (RuntimeException) throwable;
  }

  private record RequestKey(String endpoint, Object queryKey) {}
}
