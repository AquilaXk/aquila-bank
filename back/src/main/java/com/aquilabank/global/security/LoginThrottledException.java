package com.aquilabank.global.security;

/** login entrypoint throttling 결과를 공통 API error response로 전달합니다. */
public final class LoginThrottledException extends RuntimeException {

  private final LoginThrottleScope scope;
  private final long retryAfterSeconds;

  public LoginThrottledException(LoginThrottleScope scope, long retryAfterSeconds) {
    super("too many login attempts");
    if (retryAfterSeconds <= 0) {
      throw new IllegalArgumentException("retryAfterSeconds must be positive");
    }
    this.scope = scope;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public LoginThrottleScope scope() {
    return scope;
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
