package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.OutboxStaleRecoveryResult;
import java.time.Instant;

/** stale recovery 는 직접 publish 가 아니라 재진입 건수만 응답합니다. */
public record OutboxStaleRecoveryResponse(
    Instant recoveredAt, long staleAfterSeconds, int recoveredCount) {

  public static OutboxStaleRecoveryResponse from(OutboxStaleRecoveryResult result) {
    return new OutboxStaleRecoveryResponse(
        result.recoveredAt(), result.staleAfter().toSeconds(), result.recoveredCount());
  }
}
