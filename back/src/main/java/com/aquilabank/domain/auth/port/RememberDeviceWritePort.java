package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.RememberDeviceIssueCommand;
import com.aquilabank.domain.auth.model.RememberDeviceRevokeCommand;
import com.aquilabank.domain.auth.model.RememberDeviceRotateCommand;
import java.time.Instant;

/** remember device 발급/회전/revoke 저장을 쓰기 port로 분리합니다. */
public interface RememberDeviceWritePort {

  void issue(RememberDeviceIssueCommand command);

  void rotate(RememberDeviceRotateCommand command);

  void revoke(RememberDeviceRevokeCommand command);

  void revokeActiveByUserId(long userId, Instant revokedAt);
}
