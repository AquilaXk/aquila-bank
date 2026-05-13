package com.aquilabank.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.aquilabank.domain.ledger.port.TransferLimitPolicyOverrideReadPort;
import com.aquilabank.domain.ledger.port.TransferLimitUsageReadPort;
import com.aquilabank.domain.ledger.usecase.TransferLimitPolicyUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TransferLimitPolicyConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withUserConfiguration(TransferLimitPolicyConfiguration.class)
          .withBean(TransferLimitUsageReadPort.class, () -> mock(TransferLimitUsageReadPort.class))
          .withBean(
              TransferLimitPolicyOverrideReadPort.class,
              () -> mock(TransferLimitPolicyOverrideReadPort.class))
          .withPropertyValues(
              "ledger.transfer-limit.single-transfer-limit-minor=1000000",
              "ledger.transfer-limit.daily-transfer-limit-minor=5000000",
              "ledger.transfer-limit.business-zone-id=Asia/Seoul");

  @Test
  void createsTransferLimitPolicyUseCase() {
    contextRunner.run(
        context -> {
          assertThat(context).hasSingleBean(TransferLimitPolicyUseCase.class);
          assertThat(context).hasSingleBean(TransferLimitPolicyProperties.class);
        });
  }

  @Test
  void rejectsNonPositiveSingleLimit() {
    contextRunner
        .withPropertyValues("ledger.transfer-limit.single-transfer-limit-minor=0")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "ledger.transfer-limit.single-transfer-limit-minor must be positive");
            });
  }

  @Test
  void rejectsDailyLimitSmallerThanSingleLimit() {
    contextRunner
        .withPropertyValues("ledger.transfer-limit.daily-transfer-limit-minor=999999")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseMessage(
                      "ledger.transfer-limit.daily-transfer-limit-minor must be greater than or equal to single-transfer-limit-minor");
            });
  }
}
