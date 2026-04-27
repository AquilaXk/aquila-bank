package com.aquilabank.global.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.notification.model.OutboxFailedEvent;
import com.aquilabank.domain.notification.model.OutboxOpsSummary;
import com.aquilabank.domain.notification.usecase.OutboxOpsQueryUseCase;
import com.aquilabank.global.config.OutboxOpsProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

final class OutboxHealthIndicatorTest {

  @Test
  void healthIsUpWhenNoDispatchableBacklogExists() {
    Instant observedAt = Instant.parse("2026-04-27T00:00:00Z");
    OutboxHealthIndicator indicator =
        new OutboxHealthIndicator(
            queryUseCase(new OutboxOpsSummary(observedAt, null, Duration.ZERO, 0, 0, 0, 0)),
            new OutboxOpsProperties.Health(120, 0, 0));

    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails())
        .containsEntry("observedAt", observedAt)
        .containsEntry("oldestDispatchableAt", "none")
        .containsEntry("lagSeconds", 0L);
  }

  @Test
  void healthIsOutOfServiceWhenLagThresholdIsExceeded() {
    Instant observedAt = Instant.parse("2026-04-27T00:00:00Z");
    Instant oldestDispatchableAt = observedAt.minusSeconds(121);
    OutboxHealthIndicator indicator =
        new OutboxHealthIndicator(
            queryUseCase(
                new OutboxOpsSummary(
                    observedAt, oldestDispatchableAt, Duration.ofSeconds(121), 0, 0, 0, 0)),
            new OutboxOpsProperties.Health(120, 0, 0));

    Health health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.OUT_OF_SERVICE);
    assertThat(health.getDetails())
        .containsEntry("oldestDispatchableAt", oldestDispatchableAt)
        .containsEntry("lagSeconds", 121L)
        .containsEntry("reasons", List.of("lag"));
  }

  private static OutboxOpsQueryUseCase queryUseCase(OutboxOpsSummary summary) {
    return new OutboxOpsQueryUseCase() {
      @Override
      public List<OutboxFailedEvent> getFailedEvents(int limit) {
        throw new UnsupportedOperationException("not used");
      }

      @Override
      public OutboxOpsSummary getSummary() {
        return summary;
      }
    };
  }
}
