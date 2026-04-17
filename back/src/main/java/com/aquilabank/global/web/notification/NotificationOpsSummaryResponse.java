package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.NotificationOpsSummary;
import java.time.Instant;

/** consumer lag 와 DLQ count 를 runbook triage 에 바로 쓰는 summary 응답입니다. */
public record NotificationOpsSummaryResponse(
    Instant observedAt,
    String consumerGroupId,
    String topic,
    String dlqTopic,
    long lagCount,
    long dlqCount) {

  public static NotificationOpsSummaryResponse from(NotificationOpsSummary summary) {
    return new NotificationOpsSummaryResponse(
        summary.observedAt(),
        summary.consumerGroupId(),
        summary.topic(),
        summary.dlqTopic(),
        summary.lagCount(),
        summary.dlqCount());
  }
}
