package com.aquilabank.global.web.ledger;

import com.aquilabank.domain.ledger.model.CommandIdempotencyStaleRecoveryResult;
import java.time.Instant;

/** stale STARTED row 회수는 직접 송금 실행이 아니라 재진입 가능 상태만 응답합니다. */
public record CommandIdempotencyStaleRecoveryResponse(
    Instant recoveredAt, long staleAfterSeconds, int recoveredCount) {

  public static CommandIdempotencyStaleRecoveryResponse from(
      CommandIdempotencyStaleRecoveryResult result) {
    return new CommandIdempotencyStaleRecoveryResponse(
        result.recoveredAt(), result.staleAfter().toSeconds(), result.recoveredCount());
  }
}
