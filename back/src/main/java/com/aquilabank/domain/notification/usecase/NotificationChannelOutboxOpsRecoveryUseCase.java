package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationChannelOutboxRedriveResult;

public interface NotificationChannelOutboxOpsRecoveryUseCase {

  NotificationChannelOutboxRedriveResult redrive(long id);
}
