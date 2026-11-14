package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationPreference;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import com.aquilabank.domain.notification.model.NotificationPreferenceUpdateCommand;
import com.aquilabank.domain.notification.port.NotificationPreferenceWritePort;

public final class NotificationPreferenceUpdateService
    implements NotificationPreferenceUpdateUseCase {

  private static final int CHANNEL_COUNT = NotificationPreferenceChannel.values().length;

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
    int seenMask = 0;
    for (NotificationPreference item : command.items()) {
      int bit = 1 << (item.category().ordinal() * CHANNEL_COUNT + item.channel().ordinal());
      if ((seenMask & bit) != 0) {
        throw new IllegalArgumentException("duplicate preference item");
      }
      seenMask |= bit;
    }
  }
}
