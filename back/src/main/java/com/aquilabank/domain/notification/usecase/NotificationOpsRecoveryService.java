package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationDlqRedriveCommand;
import com.aquilabank.domain.notification.model.NotificationDlqRedriveResult;
import com.aquilabank.domain.notification.model.NotificationDlqRedriveTarget;
import com.aquilabank.domain.notification.port.NotificationOpsRecoveryPort;

/** notification DLQ redrive 는 ops adapter 세부 구현을 domain 밖으로 숨깁니다. */
public final class NotificationOpsRecoveryService implements NotificationOpsRecoveryUseCase {

  private final NotificationOpsRecoveryPort notificationOpsRecoveryPort;

  public NotificationOpsRecoveryService(NotificationOpsRecoveryPort notificationOpsRecoveryPort) {
    if (notificationOpsRecoveryPort == null) {
      throw new IllegalArgumentException("notificationOpsRecoveryPort is required");
    }
    this.notificationOpsRecoveryPort = notificationOpsRecoveryPort;
  }

  @Override
  public NotificationDlqRedriveResult redrive(NotificationDlqRedriveTarget target) {
    if (target == null) {
      throw new IllegalArgumentException("target is required");
    }
    return redrive(new NotificationDlqRedriveCommand(target, "system", "unknown"));
  }

  @Override
  public NotificationDlqRedriveResult redrive(NotificationDlqRedriveCommand command) {
    if (command == null) {
      throw new IllegalArgumentException("command is required");
    }
    return notificationOpsRecoveryPort.redrive(command);
  }
}
