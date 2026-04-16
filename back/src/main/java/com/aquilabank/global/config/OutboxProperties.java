package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Runtime knobs for poll cadence, retry staleness, and batch size. */
@ConfigurationProperties(prefix = "outbox.poller")
public record OutboxProperties(
    boolean enabled,
    long fixedDelayMs,
    long initialDelayMs,
    int batchSize,
    long staleAfterSeconds,
    long maxRetryDelaySeconds) {}
