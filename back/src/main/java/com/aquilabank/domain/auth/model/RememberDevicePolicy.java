package com.aquilabank.domain.auth.model;

import java.time.Duration;

/** remember device 유효 기간 정책을 도메인 값으로 고정합니다. */
public record RememberDevicePolicy(Duration ttl) {

  public RememberDevicePolicy {
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      throw new IllegalArgumentException("ttl must be positive");
    }
  }
}
