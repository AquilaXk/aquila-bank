package com.aquilabank.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** TOTP secret 보호와 enrollment/challenge 만료 기준 설정입니다. */
@ConfigurationProperties(prefix = "security.totp")
public record SecurityTotpProperties(
    String issuer,
    String secretEncryptionKey,
    long enrollmentTtlSeconds,
    long challengeTtlSeconds,
    int challengeMaxAttempts) {}
