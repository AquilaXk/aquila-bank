package com.aquilabank.domain.notification.model;

import java.util.List;

public record NotificationPreferenceUpdateCommand(List<NotificationPreference> items) {

  public NotificationPreferenceUpdateCommand {
    if (items == null || items.isEmpty()) {
      throw new IllegalArgumentException("preference items are required");
    }
    items = List.copyOf(items);
  }
}
