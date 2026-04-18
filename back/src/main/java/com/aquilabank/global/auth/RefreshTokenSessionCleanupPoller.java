package com.aquilabank.global.auth;

import com.aquilabank.domain.auth.usecase.RefreshTokenSessionCleanupUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 오래된 auth refresh token session row를 작은 batch로만 정리해 저장 비용 누적을 제한합니다. */
@Component
@ConditionalOnProperty(name = "auth.refresh-token-session.cleanup.enabled", havingValue = "true")
public class RefreshTokenSessionCleanupPoller {

  private static final Logger log = LoggerFactory.getLogger(RefreshTokenSessionCleanupPoller.class);

  private final RefreshTokenSessionCleanupUseCase refreshTokenSessionCleanupUseCase;

  public RefreshTokenSessionCleanupPoller(
      RefreshTokenSessionCleanupUseCase refreshTokenSessionCleanupUseCase) {
    this.refreshTokenSessionCleanupUseCase = refreshTokenSessionCleanupUseCase;
  }

  @Scheduled(
      fixedDelayString = "${auth.refresh-token-session.cleanup.fixed-delay-ms:300000}",
      initialDelayString = "${auth.refresh-token-session.cleanup.initial-delay-ms:60000}")
  void cleanupExpiredSessions() {
    int deleted = refreshTokenSessionCleanupUseCase.cleanupExpiredSessions();
    if (deleted > 0) {
      log.info("deleted {} expired auth refresh token session row(s)", deleted);
    }
  }
}
