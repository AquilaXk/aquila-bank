package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationDlqRedriveCommand;
import com.aquilabank.domain.notification.model.NotificationDlqRedriveResult;
import com.aquilabank.domain.notification.model.NotificationDlqRedriveTarget;

public interface NotificationOpsRecoveryUseCase {

  NotificationDlqRedriveResult redrive(NotificationDlqRedriveTarget target);

  NotificationDlqRedriveResult redrive(NotificationDlqRedriveCommand command);
}
