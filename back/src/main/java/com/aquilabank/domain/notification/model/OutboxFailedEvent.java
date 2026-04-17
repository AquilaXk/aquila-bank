package com.aquilabank.domain.notification.model;

import java.time.Instant;

/** 운영자가 failed backlog 를 확인할 때 필요한 최소 outbox row 스냅샷 */
public record OutboxFailedEvent(
    long id,
    String aggregateType,
    String aggregateId,
    String eventType,
    String eventKey,
    int retryCount,
    Instant availableAt,
    Instant updatedAt,
    String lastError) {}
