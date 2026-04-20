package com.aquilabank.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** remember device cookie 이름과 TTL 설정입니다. */
@ConfigurationProperties(prefix = "security.mfa.remember-device")
public record SecurityRememberDeviceProperties(String cookieName, long ttlSeconds) {}
