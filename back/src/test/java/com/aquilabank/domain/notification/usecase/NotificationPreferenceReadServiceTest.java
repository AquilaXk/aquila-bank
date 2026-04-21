package com.aquilabank.domain.notification.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.notification.model.NotificationPreference;
import com.aquilabank.domain.notification.model.NotificationPreferenceCategory;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import com.aquilabank.domain.notification.port.NotificationPreferenceReadPort;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationPreferenceReadServiceTest {

  private final NotificationPreferenceReadPort notificationPreferenceReadPort =
      mock(NotificationPreferenceReadPort.class);

  private final NotificationPreferenceReadService service =
      new NotificationPreferenceReadService(notificationPreferenceReadPort);

  @Test
  void returnsDefaultPreferencesWhenStoredRowsAreMissing() {
    when(notificationPreferenceReadPort.findByUserId(55L)).thenReturn(List.of());

    List<NotificationPreference> result = service.getPreferences(55L);

    assertThat(result)
        .contains(
            new NotificationPreference(
                NotificationPreferenceCategory.TRANSACTIONAL,
                NotificationPreferenceChannel.IN_APP,
                true),
            new NotificationPreference(
                NotificationPreferenceCategory.SECURITY, NotificationPreferenceChannel.SMS, true),
            new NotificationPreference(
                NotificationPreferenceCategory.MARKETING,
                NotificationPreferenceChannel.EMAIL,
                false));
  }

  @Test
  void mergesStoredOverridesOnTopOfDefaults() {
    when(notificationPreferenceReadPort.findByUserId(55L))
        .thenReturn(
            List.of(
                new NotificationPreference(
                    NotificationPreferenceCategory.MARKETING,
                    NotificationPreferenceChannel.EMAIL,
                    true)));

    List<NotificationPreference> result = service.getPreferences(55L);

    assertThat(result)
        .contains(
            new NotificationPreference(
                NotificationPreferenceCategory.MARKETING,
                NotificationPreferenceChannel.EMAIL,
                true))
        .contains(
            new NotificationPreference(
                NotificationPreferenceCategory.SECURITY,
                NotificationPreferenceChannel.EMAIL,
                true));
  }
}
