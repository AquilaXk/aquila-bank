package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.OutboxOpsSummary;
import java.time.Instant;

/** health 가 DOWN 일 때 운영자가 바로 확인할 핵심 summary 응답 */
public record OutboxOpsSummaryResponse(
    Instant observedAt,
    Instant oldestDispatchableAt,
    long lagSeconds,
    long failedCount,
    long producerTimeoutFailedCount,
    long staleSendingCount) {

  public static OutboxOpsSummaryResponse from(OutboxOpsSummary summary) {
    return new OutboxOpsSummaryResponse(
        summary.observedAt(),
        summary.oldestDispatchableAt(),
        summary.oldestDispatchLag().toSeconds(),
        summary.failedCount(),
        summary.producerTimeoutFailedCount(),
        summary.staleSendingCount());
  }
}
