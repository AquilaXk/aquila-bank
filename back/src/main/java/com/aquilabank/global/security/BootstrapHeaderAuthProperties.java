package com.aquilabank.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "security.bootstrap-header-auth")
public record BootstrapHeaderAuthProperties(
    boolean enabled, String accountIdHeader, String subjectHeader) {}
