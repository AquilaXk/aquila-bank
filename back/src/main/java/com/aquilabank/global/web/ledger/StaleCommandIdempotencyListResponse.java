package com.aquilabank.global.web.ledger;

import com.aquilabank.domain.ledger.model.StaleCommandIdempotencyRecord;
import java.time.Instant;
import java.util.List;

public record StaleCommandIdempotencyListResponse(
    int limit, List<StaleCommandIdempotencyItemResponse> items) {

  public static StaleCommandIdempotencyListResponse from(
      List<StaleCommandIdempotencyRecord> items, int limit) {
    return new StaleCommandIdempotencyListResponse(
        limit, items.stream().map(StaleCommandIdempotencyItemResponse::from).toList());
  }

  public record StaleCommandIdempotencyItemResponse(
      String idempotencyKey, Instant lockedUntil, Instant createdAt, Instant updatedAt) {

    private static StaleCommandIdempotencyItemResponse from(StaleCommandIdempotencyRecord item) {
      return new StaleCommandIdempotencyItemResponse(
          item.idempotencyKey(), item.lockedUntil(), item.createdAt(), item.updatedAt());
    }
  }
}
