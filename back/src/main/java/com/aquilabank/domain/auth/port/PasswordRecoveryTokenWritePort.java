package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.PasswordRecoveryTokenIssueCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenUseCommand;
import java.time.Instant;

/** recovery token의 supersede, issue, 사용 처리를 write port로 분리합니다. */
public interface PasswordRecoveryTokenWritePort {

  void supersedePendingTokens(long userId, Instant updatedAt);

  void issue(PasswordRecoveryTokenIssueCommand command);

  void markUsed(PasswordRecoveryTokenUseCommand command);

  void markExpired(long tokenId, Instant expiredAt);
}
