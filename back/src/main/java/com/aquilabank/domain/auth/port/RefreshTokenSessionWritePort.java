package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.RefreshTokenSessionCreateCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSessionFamilyRevokeCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSessionFamilyRevokeResult;
import com.aquilabank.domain.auth.model.RefreshTokenSessionRevokeCommand;
import com.aquilabank.domain.auth.model.RefreshTokenSessionRotateCommand;
import java.time.Instant;

/** refresh token session 생성과 rotation 저장을 쓰기 port로 분리합니다. */
public interface RefreshTokenSessionWritePort {

  long create(RefreshTokenSessionCreateCommand command);

  void rotate(RefreshTokenSessionRotateCommand command);

  void revoke(RefreshTokenSessionRevokeCommand command);

  RefreshTokenSessionFamilyRevokeResult revokeFamily(
      RefreshTokenSessionFamilyRevokeCommand command);

  void revokeActiveSessionsByUserId(long userId, Instant revokedAt);
}
