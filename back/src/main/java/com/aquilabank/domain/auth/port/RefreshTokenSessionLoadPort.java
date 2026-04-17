package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.RefreshTokenSession;
import java.util.Optional;

/** refresh token session exact lookup을 읽기 port로 분리합니다. */
public interface RefreshTokenSessionLoadPort {

  Optional<RefreshTokenSession> findByTokenHashForUpdate(String tokenHash);
}
