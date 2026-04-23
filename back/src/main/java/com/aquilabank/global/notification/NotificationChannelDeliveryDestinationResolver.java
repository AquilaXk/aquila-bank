package com.aquilabank.global.notification;

import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import java.util.Optional;
import java.util.regex.Pattern;

/** verified contact 모델이 없으므로 loginId 형식으로만 EMAIL/SMS destination을 제한합니다. */
public final class NotificationChannelDeliveryDestinationResolver {

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,63}$", Pattern.CASE_INSENSITIVE);
  private static final Pattern E164_PHONE_PATTERN = Pattern.compile("^\\+[1-9][0-9]{7,14}$");

  public Optional<String> resolve(NotificationPreferenceChannel channel, String loginId) {
    if (channel == null || loginId == null) {
      return Optional.empty();
    }
    String value = loginId.trim();
    if (value.isEmpty()) {
      return Optional.empty();
    }
    if (channel == NotificationPreferenceChannel.EMAIL && EMAIL_PATTERN.matcher(value).matches()) {
      return Optional.of(value);
    }
    if (channel == NotificationPreferenceChannel.SMS
        && E164_PHONE_PATTERN.matcher(value).matches()) {
      return Optional.of(value);
    }
    return Optional.empty();
  }
}
