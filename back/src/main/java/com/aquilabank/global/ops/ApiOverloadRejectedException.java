package com.aquilabank.global.ops;

/** endpoint group 동시성 상한을 넘으면 queueing 없이 즉시 거절합니다. */
public final class ApiOverloadRejectedException extends RuntimeException {

  private final String group;
  private final int retryAfterSeconds;

  public ApiOverloadRejectedException(String group, int retryAfterSeconds) {
    super("api overloaded; retry later");
    this.group = group;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public String group() {
    return group;
  }

  public int retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
