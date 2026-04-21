package com.aquilabank.global.ledger;

import com.aquilabank.domain.ledger.model.LedgerSnapshotReconciliationBatchResult;
import com.aquilabank.domain.ledger.usecase.LedgerSnapshotReconciliationUseCase;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** account_id cursor를 메모리에 보관해 한 번에 작은 snapshot batch만 검증합니다. */
@Component
@ConditionalOnProperty(name = "ledger.snapshot-reconciliation.enabled", havingValue = "true")
public class LedgerSnapshotReconciliationPoller {

  private static final Logger log =
      LoggerFactory.getLogger(LedgerSnapshotReconciliationPoller.class);

  private final LedgerSnapshotReconciliationUseCase reconciliationUseCase;
  private final AtomicLong afterAccountId = new AtomicLong(0L);

  public LedgerSnapshotReconciliationPoller(
      LedgerSnapshotReconciliationUseCase reconciliationUseCase) {
    this.reconciliationUseCase = reconciliationUseCase;
  }

  @Scheduled(
      fixedDelayString = "${ledger.snapshot-reconciliation.fixed-delay-ms:300000}",
      initialDelayString = "${ledger.snapshot-reconciliation.initial-delay-ms:60000}")
  void reconcileNextBatch() {
    LedgerSnapshotReconciliationBatchResult result =
        reconciliationUseCase.reconcileNextBatch(afterAccountId.get());
    afterAccountId.set(result.hasMore() ? result.nextAccountId() : 0L);
    if (result.driftedCount() > 0 || result.resolvedCount() > 0) {
      log.info(
          "ledger snapshot reconciliation checked={}, drifted={}, resolved={}, nextAccountId={}, hasMore={}",
          result.checkedCount(),
          result.driftedCount(),
          result.resolvedCount(),
          result.nextAccountId(),
          result.hasMore());
    }
  }
}
