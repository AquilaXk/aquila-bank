package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** recovery token 사용 처리에 필요한 최소 write 모델입니다. */
public record PasswordRecoveryTokenUseCommand(long tokenId, Instant usedAt) {

  public PasswordRecoveryTokenUseCommand {
    if (tokenId <= 0) {
      throw new IllegalArgumentException("tokenId must be positive");
    }
    if (usedAt == null) {
      throw new IllegalArgumentException("usedAt is required");
    }
  }
}
