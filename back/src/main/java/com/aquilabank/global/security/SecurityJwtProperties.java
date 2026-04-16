package com.aquilabank.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Shared JWT settings for local bootstrap and deployed resource-server validation. */
@ConfigurationProperties(prefix = "security.jwt")
public record SecurityJwtProperties(String secret, String issuer) {}
