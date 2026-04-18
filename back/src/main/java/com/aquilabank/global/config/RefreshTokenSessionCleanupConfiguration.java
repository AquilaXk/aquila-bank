package com.aquilabank.global.config;

import com.aquilabank.domain.auth.port.RefreshTokenSessionCleanupPort;
import com.aquilabank.domain.auth.usecase.RefreshTokenSessionCleanupService;
import com.aquilabank.domain.auth.usecase.RefreshTokenSessionCleanupUseCase;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** auth refresh token session retention cleanup use case와 runtime 설정을 조립합니다. */
@Configuration
@EnableConfigurationProperties(RefreshTokenSessionCleanupProperties.class)
public class RefreshTokenSessionCleanupConfiguration {

  @Bean
  RefreshTokenSessionCleanupUseCase refreshTokenSessionCleanupUseCase(
      RefreshTokenSessionCleanupPort refreshTokenSessionCleanupPort,
      RefreshTokenSessionCleanupProperties refreshTokenSessionCleanupProperties) {
    return new RefreshTokenSessionCleanupService(
        refreshTokenSessionCleanupPort,
        Clock.systemUTC(),
        Duration.ofDays(refreshTokenSessionCleanupProperties.retentionDays()),
        refreshTokenSessionCleanupProperties.batchSize());
  }
}
