package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationChannelOutboxRedriveResult;
import com.aquilabank.domain.notification.port.NotificationChannelOutboxOpsRecoveryPort;
import java.time.Clock;
import java.time.Instant;

/** 수동 redrive도 직접 provider 호출 대신 queue 재진입만 허용합니다. */
public final class NotificationChannelOutboxOpsRecoveryService
    implements NotificationChannelOutboxOpsRecoveryUseCase {

  private final NotificationChannelOutboxOpsRecoveryPort recoveryPort;
  private final Clock clock;

  public NotificationChannelOutboxOpsRecoveryService(
      NotificationChannelOutboxOpsRecoveryPort recoveryPort, Clock clock) {
    if (recoveryPort == null) {
      throw new IllegalArgumentException("recoveryPort is required");
    }
    if (clock == null) {
      throw new IllegalArgumentException("clock is required");
    }
    this.recoveryPort = recoveryPort;
    this.clock = clock;
  }

  @Override
  public NotificationChannelOutboxRedriveResult redrive(long id) {
    if (id <= 0) {
      throw new IllegalArgumentException("id must be positive");
    }
    Instant requestedAt = clock.instant();
    return recoveryPort.redrive(id, requestedAt);
  }
}
