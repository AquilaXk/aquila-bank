package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.NotificationPreference;
import java.util.List;

public record NotificationPreferenceResponse(List<NotificationPreferenceItemResponse> items) {

  public static NotificationPreferenceResponse from(List<NotificationPreference> items) {
    return new NotificationPreferenceResponse(
        items.stream().map(NotificationPreferenceItemResponse::from).toList());
  }

  public record NotificationPreferenceItemResponse(
      String category, String channel, boolean enabled) {

    static NotificationPreferenceItemResponse from(NotificationPreference item) {
      return new NotificationPreferenceItemResponse(
          item.category().name(), item.channel().name(), item.enabled());
    }
  }
}
