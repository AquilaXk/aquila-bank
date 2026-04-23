package com.aquilabank.domain.notification.usecase;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OutboxDispatchAdaptivePolicyTest {

  @Test
  void shrinksBatchAndIncreasesDelayAfterFailure() {
    OutboxDispatchAdaptivePolicy policy =
        new OutboxDispatchAdaptivePolicy(20, 5, Duration.ofMillis(1000), Duration.ofMillis(8000));

    policy.record(new OutboxDispatchResult(20, 19, 1));

    assertThat(policy.currentBatchSize()).isEqualTo(10);
    assertThat(policy.currentDelay()).isEqualTo(Duration.ofMillis(2000));
  }

  @Test
  void increasesDelayOnlyWhenNoEventIsClaimed() {
    OutboxDispatchAdaptivePolicy policy =
        new OutboxDispatchAdaptivePolicy(20, 5, Duration.ofMillis(1000), Duration.ofMillis(8000));

    policy.record(new OutboxDispatchResult(0, 0, 0));

    assertThat(policy.currentBatchSize()).isEqualTo(20);
    assertThat(policy.currentDelay()).isEqualTo(Duration.ofMillis(2000));
  }

  @Test
  void restoresBatchAndDelayAfterFullSuccess() {
    OutboxDispatchAdaptivePolicy policy =
        new OutboxDispatchAdaptivePolicy(20, 5, Duration.ofMillis(1000), Duration.ofMillis(8000));
    policy.record(new OutboxDispatchResult(20, 18, 2));
    policy.record(new OutboxDispatchResult(10, 10, 0));
    policy.record(new OutboxDispatchResult(20, 20, 0));

    assertThat(policy.currentBatchSize()).isEqualTo(20);
    assertThat(policy.currentDelay()).isEqualTo(Duration.ofMillis(1000));
  }

  @Test
  void keepsBatchAndDelayWithinBounds() {
    OutboxDispatchAdaptivePolicy policy =
        new OutboxDispatchAdaptivePolicy(20, 5, Duration.ofMillis(1000), Duration.ofMillis(8000));

    for (int index = 0; index < 8; index++) {
      policy.record(new OutboxDispatchResult(20, 0, 20));
    }

    assertThat(policy.currentBatchSize()).isEqualTo(5);
    assertThat(policy.currentDelay()).isEqualTo(Duration.ofMillis(8000));
  }
}
