package com.aquilabank.global.transaction;

import com.aquilabank.domain.transaction.usecase.TransactionReadModelRetentionCleanupUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 오래된 transaction read model row를 archive table로 옮겨 hot index 비용을 제한합니다. */
@Component
@ConditionalOnProperty(name = "transaction.read-model.cleanup.enabled", havingValue = "true")
public class TransactionReadModelRetentionCleanupPoller {

  private static final Logger log =
      LoggerFactory.getLogger(TransactionReadModelRetentionCleanupPoller.class);

  private final TransactionReadModelRetentionCleanupUseCase cleanupUseCase;

  public TransactionReadModelRetentionCleanupPoller(
      TransactionReadModelRetentionCleanupUseCase cleanupUseCase) {
    this.cleanupUseCase = cleanupUseCase;
  }

  @Scheduled(
      fixedDelayString = "${transaction.read-model.cleanup.fixed-delay-ms:300000}",
      initialDelayString = "${transaction.read-model.cleanup.initial-delay-ms:60000}")
  void archiveExpiredReadModels() {
    int archived = cleanupUseCase.archiveExpiredReadModels();
    if (archived > 0) {
      log.info("archived {} expired transaction read model row(s)", archived);
    }
  }
}
