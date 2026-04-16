package com.aquilabank.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 로컬/bootstrap 요청의 header 기반 account context 설정 */
@ConfigurationProperties(prefix = "security.bootstrap-header-auth")
public record BootstrapHeaderAuthProperties(
    boolean enabled, String accountIdHeader, String subjectHeader) {}
