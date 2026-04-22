package com.aquilabank.global.ops;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ApiAdmissionPermit {

  private static final ApiAdmissionPermit IGNORED = new ApiAdmissionPermit(true, "", 0, () -> {});

  private final boolean allowed;
  private final String group;
  private final int retryAfterSeconds;
  private final Runnable releaseCallback;
  private final AtomicBoolean released = new AtomicBoolean();

  private ApiAdmissionPermit(
      boolean allowed, String group, int retryAfterSeconds, Runnable releaseCallback) {
    this.allowed = allowed;
    this.group = group;
    this.retryAfterSeconds = retryAfterSeconds;
    this.releaseCallback = releaseCallback;
  }

  public static ApiAdmissionPermit acquired(String group, Runnable releaseCallback) {
    return new ApiAdmissionPermit(true, group, 0, releaseCallback);
  }

  public static ApiAdmissionPermit rejected(String group, int retryAfterSeconds) {
    return new ApiAdmissionPermit(false, group, retryAfterSeconds, () -> {});
  }

  public static ApiAdmissionPermit ignored() {
    return IGNORED;
  }

  public boolean allowed() {
    return allowed;
  }

  public String group() {
    return group;
  }

  public int retryAfterSeconds() {
    return retryAfterSeconds;
  }

  public void release() {
    if (allowed && released.compareAndSet(false, true)) {
      releaseCallback.run();
    }
  }
}
