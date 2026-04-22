package com.aquilabank.global.security;

/** 공개 auth entrypoint throttling 결과를 공통 API error response로 전달합니다. */
public final class LoginThrottledException extends RuntimeException {

  private final LoginThrottleScope scope;
  private final long retryAfterSeconds;

  public LoginThrottledException(LoginThrottleScope scope, long retryAfterSeconds) {
    this(scope, retryAfterSeconds, "too many login attempts");
  }

  public LoginThrottledException(LoginThrottleScope scope, long retryAfterSeconds, String message) {
    super(message);
    if (retryAfterSeconds <= 0) {
      throw new IllegalArgumentException("retryAfterSeconds must be positive");
    }
    if (message == null || message.isBlank()) {
      throw new IllegalArgumentException("message is required");
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
