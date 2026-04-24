package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryOutboxItem;
import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryResult;
import com.aquilabank.domain.auth.model.PasswordRecoveryDeliverySkipReason;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenQueryRecord;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenStatus;
import com.aquilabank.domain.auth.port.PasswordRecoveryDeliveryOutboxDispatchPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryDeliveryPort;
import com.aquilabank.domain.auth.port.PasswordRecoverySecretPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryTokenQueryPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** password recovery delivery outbox를 provider 호출과 retry 상태 전이로 비웁니다. */
public final class PasswordRecoveryDeliveryWorkerService
    implements PasswordRecoveryDeliveryWorkerUseCase {

  private static final int MAX_BACKOFF_POWER = 8;
  private static final int MAX_ERROR_LENGTH = 300;

  private final PasswordRecoveryDeliveryOutboxDispatchPort dispatchPort;
  private final PasswordRecoveryTokenQueryPort tokenQueryPort;
  private final PasswordRecoverySecretPort secretPort;
  private final PasswordRecoveryDeliveryPort deliveryPort;
  private final Clock clock;
  private final int batchSize;
  private final Duration retryBaseDelay;
  private final Duration maxRetryDelay;
  private final int maxRetryAttempts;

  public PasswordRecoveryDeliveryWorkerService(
      PasswordRecoveryDeliveryOutboxDispatchPort dispatchPort,
      PasswordRecoveryTokenQueryPort tokenQueryPort,
      PasswordRecoverySecretPort secretPort,
      PasswordRecoveryDeliveryPort deliveryPort,
      Clock clock,
      int batchSize,
      Duration retryBaseDelay,
      Duration maxRetryDelay,
      int maxRetryAttempts) {
    this.dispatchPort = Objects.requireNonNull(dispatchPort, "dispatchPort");
    this.tokenQueryPort = Objects.requireNonNull(tokenQueryPort, "tokenQueryPort");
    this.secretPort = Objects.requireNonNull(secretPort, "secretPort");
    this.deliveryPort = Objects.requireNonNull(deliveryPort, "deliveryPort");
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
    List<PasswordRecoveryDeliveryOutboxItem> items = dispatchPort.claimPending(batchSize, now);
    for (PasswordRecoveryDeliveryOutboxItem item : items) {
      dispatchSingle(item, now);
    }
    return items.size();
  }

  private void dispatchSingle(PasswordRecoveryDeliveryOutboxItem item, Instant now) {
    PasswordRecoveryTokenQueryRecord tokenRecord =
        tokenQueryPort.findByRequestId(item.requestId()).orElse(null);
    PasswordRecoveryDeliverySkipReason tokenSkipReason = tokenSkipReason(tokenRecord, now);
    if (tokenSkipReason != null) {
      // 이미 사용/만료/정리된 token은 provider 미호출 완료 사유로 남깁니다.
      dispatchPort.markSkipped(item.id(), now, tokenSkipReason);
      return;
    }

    String recoveryToken =
        secretPort.reveal(tokenRecord.tokenCiphertext(), tokenRecord.tokenNonce());
    try {
      PasswordRecoveryDeliveryResult result =
          Objects.requireNonNull(
              deliveryPort.deliver(
                  new PasswordRecoveryDeliveryCommand(
                      item.requestId(),
                      item.userId(),
                      item.deliveryChannel(),
                      item.providerDestination(),
                      recoveryToken,
                      tokenRecord.expiresAt(),
                      tokenRecord.createdAt())),
              "password recovery delivery result");
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

  private PasswordRecoveryDeliverySkipReason tokenSkipReason(
      PasswordRecoveryTokenQueryRecord tokenRecord, Instant now) {
    if (tokenRecord == null) {
      return PasswordRecoveryDeliverySkipReason.TOKEN_MISSING;
    }
    if (tokenRecord.tokenStatus() != PasswordRecoveryTokenStatus.PENDING) {
      return PasswordRecoveryDeliverySkipReason.TOKEN_NOT_PENDING;
    }
    if (!tokenRecord.expiresAt().isAfter(now)) {
      return PasswordRecoveryDeliverySkipReason.TOKEN_EXPIRED;
    }
    return null;
  }

  private Duration computeBackoff(int retryCount) {
    // provider 장애 시 auth recovery queue 폭주를 막기 위한 bounded exponential backoff
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
      return "password recovery delivery failed";
    }
    return errorMessage.length() <= MAX_ERROR_LENGTH
        ? errorMessage
        : errorMessage.substring(0, MAX_ERROR_LENGTH);
  }
}
