package com.aquilabank.domain.auth.model;

import java.time.Duration;

/** 로그인 실패 누적과 임시 잠금 기준을 domain 값으로 고정합니다. */
public record LoginProtectionPolicy(int maxFailures, Duration lockDuration, Duration resetWindow) {

  public LoginProtectionPolicy {
    if (maxFailures <= 0) {
      throw new IllegalArgumentException("maxFailures must be positive");
    }
    if (lockDuration == null || lockDuration.isZero() || lockDuration.isNegative()) {
      throw new IllegalArgumentException("lockDuration must be positive");
    }
    if (resetWindow == null || resetWindow.isZero() || resetWindow.isNegative()) {
      throw new IllegalArgumentException("resetWindow must be positive");
    }
  }
}
