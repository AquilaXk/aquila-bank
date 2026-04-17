package com.aquilabank.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 로컬 bootstrap과 배포 resource server 검증이 함께 쓰는 JWT 설정 */
@ConfigurationProperties(prefix = "security.jwt")
public record SecurityJwtProperties(
    String secret, String issuer, long accessTokenTtlSeconds, long refreshTokenTtlSeconds) {}
