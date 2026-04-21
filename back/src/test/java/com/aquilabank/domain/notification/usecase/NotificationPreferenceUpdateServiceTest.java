package com.aquilabank.domain.notification.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.aquilabank.domain.notification.model.NotificationPreference;
import com.aquilabank.domain.notification.model.NotificationPreferenceCategory;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import com.aquilabank.domain.notification.model.NotificationPreferenceUpdateCommand;
import com.aquilabank.domain.notification.port.NotificationPreferenceWritePort;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationPreferenceUpdateServiceTest {

  private final NotificationPreferenceWritePort notificationPreferenceWritePort =
      mock(NotificationPreferenceWritePort.class);

  private final NotificationPreferenceUpdateService service =
      new NotificationPreferenceUpdateService(notificationPreferenceWritePort);

  @Test
  void upsertsPreferenceRowsForUser() {
    NotificationPreferenceUpdateCommand command =
        new NotificationPreferenceUpdateCommand(
            List.of(
                new NotificationPreference(
                    NotificationPreferenceCategory.MARKETING,
                    NotificationPreferenceChannel.EMAIL,
                    true),
                new NotificationPreference(
                    NotificationPreferenceCategory.SECURITY,
                    NotificationPreferenceChannel.SMS,
                    false)));

    service.updatePreferences(55L, command);

    verify(notificationPreferenceWritePort).upsert(55L, command.items());
  }

  @Test
  void rejectsDuplicateCategoryAndChannelPairs() {
    NotificationPreference item =
        new NotificationPreference(
            NotificationPreferenceCategory.MARKETING, NotificationPreferenceChannel.EMAIL, true);

    NotificationPreferenceUpdateCommand command =
        new NotificationPreferenceUpdateCommand(List.of(item, item));

    assertThatThrownBy(() -> service.updatePreferences(55L, command))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("duplicate preference item");
  }
}
