package com.aquilabank.domain.notification.model;

public record NotificationPreference(
    NotificationPreferenceCategory category,
    NotificationPreferenceChannel channel,
    boolean enabled) {

  public NotificationPreference {
    if (category == null) {
      throw new IllegalArgumentException("category is required");
    }
    if (channel == null) {
      throw new IllegalArgumentException("channel is required");
    }
  }
}
