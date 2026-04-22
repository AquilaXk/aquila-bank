package com.aquilabank.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** password recovery token 전달 adapter 활성화 설정입니다. */
@ConfigurationProperties(prefix = "auth.password-recovery.delivery")
public record PasswordRecoveryDeliveryProperties(boolean enabled) {}
