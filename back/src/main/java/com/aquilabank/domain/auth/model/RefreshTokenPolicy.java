package com.aquilabank.domain.auth.model;

import java.time.Duration;

/** refresh token 만료 기준을 domain 값으로 고정합니다. */
public record RefreshTokenPolicy(Duration ttl) {

  public RefreshTokenPolicy {
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("ttl must be positive");
    }
  }
}
