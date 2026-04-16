package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "outbox.poller")
public record OutboxProperties(
    boolean enabled,
    long fixedDelayMs,
    long initialDelayMs,
    int batchSize,
    long staleAfterSeconds,
    long maxRetryDelaySeconds) {}
