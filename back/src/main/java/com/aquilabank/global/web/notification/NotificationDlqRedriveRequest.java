package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.NotificationDlqRedriveCommand;
import com.aquilabank.domain.notification.model.NotificationDlqRedriveTarget;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/** preview 좌표를 그대로 받아 단건 redrive 범위를 고정합니다. */
public record NotificationDlqRedriveRequest(
    @NotNull(message = "partition is required") @PositiveOrZero(message = "partition must not be negative") Integer partition,
    @NotNull(message = "offset is required") @PositiveOrZero(message = "offset must not be negative") Long offset) {

  NotificationDlqRedriveTarget toTarget() {
    return new NotificationDlqRedriveTarget(partition, offset);
  }

  NotificationDlqRedriveCommand toCommand(String actor, String requestId) {
    return new NotificationDlqRedriveCommand(toTarget(), actor, requestId);
  }
}
