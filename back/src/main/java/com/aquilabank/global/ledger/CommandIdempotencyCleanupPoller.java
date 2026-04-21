package com.aquilabank.global.ledger;

import com.aquilabank.domain.ledger.usecase.CommandIdempotencyCleanupUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 오래된 command idempotency 완료/실패 row를 작은 batch로만 정리합니다. */
@Component
@ConditionalOnProperty(name = "ledger.command-idempotency.cleanup.enabled", havingValue = "true")
public class CommandIdempotencyCleanupPoller {

  private static final Logger log = LoggerFactory.getLogger(CommandIdempotencyCleanupPoller.class);

  private final CommandIdempotencyCleanupUseCase cleanupUseCase;

  public CommandIdempotencyCleanupPoller(CommandIdempotencyCleanupUseCase cleanupUseCase) {
    this.cleanupUseCase = cleanupUseCase;
  }

  @Scheduled(
      fixedDelayString = "${ledger.command-idempotency.cleanup.fixed-delay-ms:300000}",
      initialDelayString = "${ledger.command-idempotency.cleanup.initial-delay-ms:60000}")
  void cleanupExpiredRecords() {
    int deleted = cleanupUseCase.cleanupExpiredRecords();
    if (deleted > 0) {
      log.info("deleted {} command idempotency row(s)", deleted);
    }
  }
}
