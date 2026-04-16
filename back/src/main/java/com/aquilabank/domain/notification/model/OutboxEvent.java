package com.aquilabank.domain.notification.model;

import java.time.Instant;

/** dispatch 대상으로 claim한 outbox row 스냅샷 */
public record OutboxEvent(
    long id,
    String aggregateType,
    String aggregateId,
    String eventType,
    String eventKey,
    String payload,
    int retryCount,
    Instant availableAt,
    Instant updatedAt) {}
