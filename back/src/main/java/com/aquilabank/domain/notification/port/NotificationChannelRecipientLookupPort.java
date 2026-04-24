package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.NotificationPreferenceChannel;
import java.util.Optional;

/** provider delivery 직전에 `userId + channel` 기준 verified destination을 조회합니다. */
public interface NotificationChannelRecipientLookupPort {

  Optional<String> findProviderDestination(long userId, NotificationPreferenceChannel channel);
}
