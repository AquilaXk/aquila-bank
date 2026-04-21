package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aquilabank.domain.ledger.port.LedgerSnapshotDriftReadPort;
import com.aquilabank.domain.ledger.port.LedgerSnapshotReconciliationPort;
import com.aquilabank.domain.ledger.port.LedgerSnapshotRecoveryPort;
import com.aquilabank.domain.ledger.usecase.LedgerSnapshotDriftQueryUseCase;
import com.aquilabank.domain.ledger.usecase.LedgerSnapshotReconciliationUseCase;
import com.aquilabank.domain.ledger.usecase.LedgerSnapshotRecoveryUseCase;
import com.aquilabank.global.ledger.LedgerSnapshotReconciliationPoller;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class LedgerSnapshotReconciliationConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(
              LedgerSnapshotReconciliationConfiguration.class,
              LedgerSnapshotReconciliationPoller.class)
          .withBean(
              LedgerSnapshotReconciliationPort.class,
              () -> mock(LedgerSnapshotReconciliationPort.class))
          .withBean(
              LedgerSnapshotDriftReadPort.class, () -> mock(LedgerSnapshotDriftReadPort.class))
          .withBean(LedgerSnapshotRecoveryPort.class, () -> mock(LedgerSnapshotRecoveryPort.class))
          .withPropertyValues(
              "ledger.snapshot-reconciliation.fixed-delay-ms=300000",
              "ledger.snapshot-reconciliation.initial-delay-ms=60000",
              "ledger.snapshot-reconciliation.batch-size=100",
              "ledger.snapshot-reconciliation.drift-list-limit=50",
              "ledger.snapshot-reconciliation.recovery-reason-max-length=200");

  @Test
  void createsPollerWhenReconciliationIsEnabled() {
    contextRunner
        .withPropertyValues("ledger.snapshot-reconciliation.enabled=true")
        .run(
            context -> {
              assertThat(context).hasSingleBean(LedgerSnapshotReconciliationUseCase.class);
              assertThat(context).hasSingleBean(LedgerSnapshotDriftQueryUseCase.class);
              assertThat(context).hasSingleBean(LedgerSnapshotRecoveryUseCase.class);
              assertThat(context).hasSingleBean(LedgerSnapshotReconciliationPoller.class);
            });
  }

  @Test
  void doesNotCreatePollerWhenReconciliationIsDisabled() {
    contextRunner
        .withPropertyValues("ledger.snapshot-reconciliation.enabled=false")
        .run(
            context -> {
              assertThat(context).hasSingleBean(LedgerSnapshotReconciliationUseCase.class);
              assertThat(context).hasSingleBean(LedgerSnapshotDriftQueryUseCase.class);
              assertThat(context).hasSingleBean(LedgerSnapshotRecoveryUseCase.class);
              assertThat(context).doesNotHaveBean(LedgerSnapshotReconciliationPoller.class);
            });
  }

  @Test
  void rejectsNonPositiveBatchSize() {
    contextRunner
        .withPropertyValues(
            "ledger.snapshot-reconciliation.enabled=true",
            "ledger.snapshot-reconciliation.batch-size=0")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "ledger.snapshot-reconciliation.batch-size must be positive");
            });
  }

  @Test
  void rejectsRecoveryReasonLengthAboveStorageLimit() {
    contextRunner
        .withPropertyValues(
            "ledger.snapshot-reconciliation.enabled=true",
            "ledger.snapshot-reconciliation.recovery-reason-max-length=201")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "ledger.snapshot-reconciliation.recovery-reason-max-length must be between 1 and 200");
            });
  }
}
