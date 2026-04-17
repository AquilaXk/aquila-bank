package com.aquilabank.global.security;

import com.aquilabank.global.config.OutboxOpsProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 내부 운영 API 전용 service JWT 발급/검증 빈을 분리해 public JWT와 경계를 고정합니다. */
@Configuration
@EnableConfigurationProperties(InternalServiceTokenProperties.class)
public class InternalServiceTokenConfiguration {

  @Bean
  InternalServiceTokenVerifier internalServiceTokenVerifier(
      InternalServiceTokenProperties internalServiceTokenProperties,
      AccountBootstrapApiProperties accountBootstrapApiProperties,
      AuthBootstrapApiProperties authBootstrapApiProperties,
      OutboxOpsProperties outboxOpsProperties) {
    return new InternalServiceTokenVerifier(
        internalServiceTokenProperties,
        accountBootstrapApiProperties,
        authBootstrapApiProperties,
        outboxOpsProperties);
  }

  @Bean
  InternalServiceTokenIssuer internalServiceTokenIssuer(
      InternalServiceTokenProperties internalServiceTokenProperties) {
    return new InternalServiceTokenIssuer(internalServiceTokenProperties);
  }
}
