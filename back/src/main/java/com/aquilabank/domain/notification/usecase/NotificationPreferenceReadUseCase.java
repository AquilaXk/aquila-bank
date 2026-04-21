package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationPreference;
import java.util.List;

public interface NotificationPreferenceReadUseCase {

  List<NotificationPreference> getPreferences(long userId);
}
