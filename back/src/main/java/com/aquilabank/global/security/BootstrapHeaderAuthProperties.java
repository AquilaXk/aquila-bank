package com.aquilabank.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Allows local/bootstrap callers to inject an account context through HTTP headers. */
@ConfigurationProperties(prefix = "security.bootstrap-header-auth")
public record BootstrapHeaderAuthProperties(
    boolean enabled, String accountIdHeader, String subjectHeader) {}
