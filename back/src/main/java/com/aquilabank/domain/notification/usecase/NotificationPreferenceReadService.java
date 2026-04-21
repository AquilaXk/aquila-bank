package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.NotificationPreference;
import com.aquilabank.domain.notification.model.NotificationPreferenceCategory;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import com.aquilabank.domain.notification.port.NotificationPreferenceReadPort;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** preference는 소형 고정 조합이라 user 단위 조회 후 기본값 merge가 가장 단순하고 안정적입니다. */
public final class NotificationPreferenceReadService implements NotificationPreferenceReadUseCase {

  private final NotificationPreferenceReadPort notificationPreferenceReadPort;

  public NotificationPreferenceReadService(
      NotificationPreferenceReadPort notificationPreferenceReadPort) {
    this.notificationPreferenceReadPort = notificationPreferenceReadPort;
  }

  @Override
  public List<NotificationPreference> getPreferences(long userId) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    Map<String, NotificationPreference> items = new LinkedHashMap<>();
    for (NotificationPreference item : defaultPreferences()) {
      items.put(key(item), item);
    }
    for (NotificationPreference item : notificationPreferenceReadPort.findByUserId(userId)) {
      items.put(key(item), item);
    }
    return List.copyOf(items.values());
  }

  private static List<NotificationPreference> defaultPreferences() {
    return List.of(
        new NotificationPreference(
            NotificationPreferenceCategory.TRANSACTIONAL,
            NotificationPreferenceChannel.IN_APP,
            true),
        new NotificationPreference(
            NotificationPreferenceCategory.TRANSACTIONAL,
            NotificationPreferenceChannel.EMAIL,
            true),
        new NotificationPreference(
            NotificationPreferenceCategory.TRANSACTIONAL, NotificationPreferenceChannel.SMS, false),
        new NotificationPreference(
            NotificationPreferenceCategory.SECURITY, NotificationPreferenceChannel.IN_APP, true),
        new NotificationPreference(
            NotificationPreferenceCategory.SECURITY, NotificationPreferenceChannel.EMAIL, true),
        new NotificationPreference(
            NotificationPreferenceCategory.SECURITY, NotificationPreferenceChannel.SMS, true),
        new NotificationPreference(
            NotificationPreferenceCategory.MARKETING, NotificationPreferenceChannel.IN_APP, false),
        new NotificationPreference(
            NotificationPreferenceCategory.MARKETING, NotificationPreferenceChannel.EMAIL, false),
        new NotificationPreference(
            NotificationPreferenceCategory.MARKETING, NotificationPreferenceChannel.SMS, false));
  }

  private static String key(NotificationPreference item) {
    return item.category().name() + ":" + item.channel().name();
  }
}
