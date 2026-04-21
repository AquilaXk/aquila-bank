package com.aquilabank.domain.notification.model;

import java.time.Instant;

/** DLQ redrive audit history 조회용 row snapshot */
public record NotificationDlqRedriveAuditItem(
    long id,
    String actor,
    String requestId,
    String sourceTopic,
    int sourcePartition,
    long sourceOffset,
    String eventKey,
    String targetTopic,
    Integer targetPartition,
    Long targetOffset,
    NotificationDlqRedriveOutcome outcome,
    String errorMessage,
    Instant redrivenAt,
    Instant createdAt) {}
