package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationPreferenceUpdateCommand;

public interface NotificationPreferenceUpdateUseCase {

  void updatePreferences(long userId, NotificationPreferenceUpdateCommand command);
}
