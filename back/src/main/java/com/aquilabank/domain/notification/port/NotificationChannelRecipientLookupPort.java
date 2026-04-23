package com.aquilabank.domain.notification.port;

import java.util.Optional;

/** provider delivery 직전에 `userId` 기준 loginId를 조회하는 notification 전용 port */
public interface NotificationChannelRecipientLookupPort {

  Optional<String> findLoginIdByUserId(long userId);
}
