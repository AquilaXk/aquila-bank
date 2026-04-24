package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationChannelDeliveryResult;
import com.aquilabank.domain.notification.model.NotificationChannelOutboxItem;
import com.aquilabank.domain.notification.port.NotificationChannelOutboxDispatchPort;
import com.aquilabank.domain.notification.port.NotificationChannelProviderPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** channel outbox row를 provider로 전달하고 retry 상태를 전진시키는 domain service */
public final class NotificationChannelProviderWorkerService
    implements NotificationChannelProviderWorkerUseCase {

  private static final int MAX_BACKOFF_POWER = 8;
  private static final int MAX_ERROR_LENGTH = 300;

  private final NotificationChannelOutboxDispatchPort dispatchPort;
  private final NotificationChannelProviderPort providerPort;
  private final Clock clock;
  private final int batchSize;
  private final Duration retryBaseDelay;
  private final Duration maxRetryDelay;
  private final int maxRetryAttempts;

  public NotificationChannelProviderWorkerService(
      NotificationChannelOutboxDispatchPort dispatchPort,
      NotificationChannelProviderPort providerPort,
      Clock clock,
      int batchSize,
      Duration retryBaseDelay,
      Duration maxRetryDelay) {
    this(dispatchPort, providerPort, clock, batchSize, retryBaseDelay, maxRetryDelay, 10);
  }

  public NotificationChannelProviderWorkerService(
      NotificationChannelOutboxDispatchPort dispatchPort,
      NotificationChannelProviderPort providerPort,
      Clock clock,
      int batchSize,
      Duration retryBaseDelay,
      Duration maxRetryDelay,
      int maxRetryAttempts) {
    this.dispatchPort = Objects.requireNonNull(dispatchPort, "dispatchPort");
    this.providerPort = Objects.requireNonNull(providerPort, "providerPort");
    this.clock = Objects.requireNonNull(clock, "clock");
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be positive");
    }
    this.batchSize = batchSize;
    this.retryBaseDelay = requirePositive(retryBaseDelay, "retryBaseDelay");
    this.maxRetryDelay = requirePositive(maxRetryDelay, "maxRetryDelay");
    if (this.maxRetryDelay.compareTo(this.retryBaseDelay) < 0) {
      throw new IllegalArgumentException("maxRetryDelay must be greater than retryBaseDelay");
    }
    if (maxRetryAttempts < 1) {
      throw new IllegalArgumentException("maxRetryAttempts must be positive");
    }
    this.maxRetryAttempts = maxRetryAttempts;
  }

  @Override
  public int dispatchDueDeliveries() {
    Instant now = clock.instant();
    List<NotificationChannelOutboxItem> items = dispatchPort.claimPending(batchSize, now);
    for (NotificationChannelOutboxItem item : items) {
      dispatchSingle(item, now);
    }
    return items.size();
  }

  private void dispatchSingle(NotificationChannelOutboxItem item, Instant now) {
    try {
      NotificationChannelDeliveryResult result =
          Objects.requireNonNull(providerPort.send(item), "provider delivery result");
      if (result.sent()) {
        dispatchPort.markSent(item.id(), now);
        return;
      }
      dispatchPort.markSkipped(item.id(), now, result.skipReason());
    } catch (RuntimeException ex) {
      String errorMessage = shorten(ex.getMessage());
      if (item.retryCount() + 1 >= maxRetryAttempts) {
        dispatchPort.markQuarantined(item.id(), now, errorMessage);
        return;
      }
      Instant nextAttemptAt = now.plus(computeBackoff(item.retryCount()));
      dispatchPort.markFailed(item.id(), nextAttemptAt, now, errorMessage);
    }
  }

  private Duration computeBackoff(int retryCount) {
    // provider 장애 시 retry 폭주 방지용 bounded exponential backoff
    int exponent = Math.min(Math.max(retryCount, 0), MAX_BACKOFF_POWER);
    Duration candidate = retryBaseDelay.multipliedBy(1L << exponent);
    return candidate.compareTo(maxRetryDelay) > 0 ? maxRetryDelay : candidate;
  }

  private Duration requirePositive(Duration value, String name) {
    Duration duration = Objects.requireNonNull(value, name);
    if (duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return duration;
  }

  private String shorten(String errorMessage) {
    if (errorMessage == null || errorMessage.isBlank()) {
      return "provider delivery failed";
    }
    return errorMessage.length() <= MAX_ERROR_LENGTH
        ? errorMessage
        : errorMessage.substring(0, MAX_ERROR_LENGTH);
  }
}
