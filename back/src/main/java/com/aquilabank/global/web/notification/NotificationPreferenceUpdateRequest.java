package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.NotificationPreference;
import com.aquilabank.domain.notification.model.NotificationPreferenceCategory;
import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record NotificationPreferenceUpdateRequest(
    @NotEmpty(message = "preference items are required") @Size(max = 32, message = "preference items size must be 32 or less") List<@Valid PreferenceItem> items) {

  public NotificationPreferenceUpdateRequest {
    items = List.copyOf(items);
    validateDuplicates(items);
  }

  private static void validateDuplicates(List<PreferenceItem> items) {
    Set<String> keys = new HashSet<>();
    for (PreferenceItem item : items) {
      String key = item.category() + ":" + item.channel();
      if (!keys.add(key)) {
        throw new IllegalArgumentException("duplicate preference item");
      }
    }
  }

  public record PreferenceItem(
      @NotNull(message = "category is required") NotificationPreferenceCategory category,
      @NotNull(message = "channel is required") NotificationPreferenceChannel channel,
      @NotNull(message = "enabled is required") Boolean enabled) {

    NotificationPreference toModel() {
      return new NotificationPreference(category, channel, enabled);
    }
  }
}
