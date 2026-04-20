package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.RememberDevice;
import java.util.Optional;

/** remember device exact lookup을 읽기 port로 분리합니다. */
public interface RememberDeviceLoadPort {

  Optional<RememberDevice> findActiveByUserIdAndTokenHashForUpdate(long userId, String tokenHash);
}
