package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationPreference;
import com.aquilabank.domain.notification.model.NotificationPreferenceUpdateCommand;
import com.aquilabank.domain.notification.port.NotificationPreferenceWritePort;
import java.util.HashSet;
import java.util.Set;

public final class NotificationPreferenceUpdateService
    implements NotificationPreferenceUpdateUseCase {

  private final NotificationPreferenceWritePort notificationPreferenceWritePort;

  public NotificationPreferenceUpdateService(
      NotificationPreferenceWritePort notificationPreferenceWritePort) {
    this.notificationPreferenceWritePort = notificationPreferenceWritePort;
  }

  @Override
  public void updatePreferences(long userId, NotificationPreferenceUpdateCommand command) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (command == null) {
      throw new IllegalArgumentException("command is required");
    }
    validateDuplicates(command);
    notificationPreferenceWritePort.upsert(userId, command.items());
  }

  private static void validateDuplicates(NotificationPreferenceUpdateCommand command) {
    Set<String> keys = new HashSet<>();
    for (NotificationPreference item : command.items()) {
      String key = item.category().name() + ":" + item.channel().name();
      if (!keys.add(key)) {
        throw new IllegalArgumentException("duplicate preference item");
      }
    }
  }
}
