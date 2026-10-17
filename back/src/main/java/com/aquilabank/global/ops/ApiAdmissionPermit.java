package com.aquilabank.global.ops;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class ApiAdmissionPermit {

  private static final Consumer<Boolean> NO_OP_RELEASE = healthy -> {};
  private static final ApiAdmissionPermit IGNORED =
      new ApiAdmissionPermit(true, "", 0, NO_OP_RELEASE);

  private final boolean allowed;
  private final String group;
  private final int retryAfterSeconds;
  private final Consumer<Boolean> releaseCallback;
  private final AtomicBoolean released = new AtomicBoolean();

  private ApiAdmissionPermit(
      boolean allowed, String group, int retryAfterSeconds, Consumer<Boolean> releaseCallback) {
    this.allowed = allowed;
    this.group = group;
    this.retryAfterSeconds = retryAfterSeconds;
    this.releaseCallback = releaseCallback;
  }

  public static ApiAdmissionPermit acquired(String group, Consumer<Boolean> releaseCallback) {
    return new ApiAdmissionPermit(true, group, 0, releaseCallback);
  }

  public static ApiAdmissionPermit rejected(String group, int retryAfterSeconds) {
    return new ApiAdmissionPermit(false, group, retryAfterSeconds, NO_OP_RELEASE);
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
    release(true);
  }

  public void release(boolean healthy) {
    if (allowed && released.compareAndSet(false, true)) {
      releaseCallback.accept(healthy);
    }
  }
}
