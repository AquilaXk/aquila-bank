package com.aquilabank.global.auth;

import com.aquilabank.domain.auth.usecase.PasswordRecoveryTokenCleanupUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 오래된 password recovery token row를 작은 batch로 정리해 credential 보존 기간을 제한합니다. */
@Component
@ConditionalOnProperty(name = "auth.password-recovery-token.cleanup.enabled", havingValue = "true")
public class PasswordRecoveryTokenCleanupPoller {

  private static final Logger log =
      LoggerFactory.getLogger(PasswordRecoveryTokenCleanupPoller.class);

  private final PasswordRecoveryTokenCleanupUseCase passwordRecoveryTokenCleanupUseCase;

  public PasswordRecoveryTokenCleanupPoller(
      PasswordRecoveryTokenCleanupUseCase passwordRecoveryTokenCleanupUseCase) {
    this.passwordRecoveryTokenCleanupUseCase = passwordRecoveryTokenCleanupUseCase;
  }

  @Scheduled(
      fixedDelayString = "${auth.password-recovery-token.cleanup.fixed-delay-ms:300000}",
      initialDelayString = "${auth.password-recovery-token.cleanup.initial-delay-ms:60000}")
  void cleanupExpiredTokens() {
    int deleted = passwordRecoveryTokenCleanupUseCase.cleanupExpiredTokens();
    if (deleted > 0) {
      log.info("deleted {} expired password recovery token row(s)", deleted);
    }
  }
}
