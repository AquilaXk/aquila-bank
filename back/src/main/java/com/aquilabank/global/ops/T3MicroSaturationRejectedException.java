package com.aquilabank.global.ops;

/** DB pool/thread/query timeout이 함께 포화될 때 protected API를 즉시 거절합니다. */
public final class T3MicroSaturationRejectedException extends RuntimeException {

  private final int retryAfterSeconds;

  public T3MicroSaturationRejectedException(int retryAfterSeconds) {
    super("server is saturated; retry later");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public int retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
