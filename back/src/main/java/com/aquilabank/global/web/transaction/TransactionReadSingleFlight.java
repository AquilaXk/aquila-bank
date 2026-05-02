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

  private final ConcurrentHashMap<RequestKey, CompletableFuture<TransactionSlice>> inFlight =
      new ConcurrentHashMap<>();

  public TransactionSlice get(
      String endpoint, TransactionQuery query, Supplier<TransactionSlice> supplier) {
    RequestKey key = new RequestKey(endpoint, query);
    CompletableFuture<TransactionSlice> ownerFuture = new CompletableFuture<>();
    CompletableFuture<TransactionSlice> existingFuture = inFlight.putIfAbsent(key, ownerFuture);
    if (existingFuture != null) {
      return await(existingFuture);
    }

    try {
      TransactionSlice slice = supplier.get();
      ownerFuture.complete(slice);
      return slice;
    } catch (RuntimeException ex) {
      ownerFuture.completeExceptionally(ex);
      throw ex;
    } finally {
      inFlight.remove(key, ownerFuture);
    }
  }

  private TransactionSlice await(CompletableFuture<TransactionSlice> future) {
    try {
      return future.join();
    } catch (CompletionException ex) {
      throw (RuntimeException) ex.getCause();
    }
  }

  private record RequestKey(String endpoint, TransactionQuery query) {}
}
