package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** poll 주기, stale 기준, batch 크기용 runtime 설정 */
@ConfigurationProperties(prefix = "outbox.poller")
public record OutboxProperties(
    boolean enabled,
    long fixedDelayMs,
    long initialDelayMs,
    int batchSize,
    long staleAfterSeconds,
    long maxRetryDelaySeconds) {}
