package com.aquilabank.domain.notification.model;

import java.time.Instant;

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
