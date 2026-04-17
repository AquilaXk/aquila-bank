package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.NotificationDlqRedriveResult;
import java.time.Instant;

/** redrive 결과는 source DLQ 좌표와 재발행 좌표를 함께 반환합니다. */
public record NotificationDlqRedriveResponse(
    String sourceTopic,
    int sourcePartition,
    long sourceOffset,
    String eventKey,
    String targetTopic,
    int targetPartition,
    long targetOffset,
    Instant redrivenAt) {

  static NotificationDlqRedriveResponse from(NotificationDlqRedriveResult item) {
    return new NotificationDlqRedriveResponse(
        item.sourceTopic(),
        item.sourcePartition(),
        item.sourceOffset(),
        item.eventKey(),
        item.targetTopic(),
        item.targetPartition(),
        item.targetOffset(),
        item.redrivenAt());
  }
}
