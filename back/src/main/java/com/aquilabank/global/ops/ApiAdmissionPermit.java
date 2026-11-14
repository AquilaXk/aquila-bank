package com.aquilabank.global.ops;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public final class ApiAdmissionPermit {

  @FunctionalInterface
  interface ReleaseTarget {
    void release(boolean healthy);
  }

  private static final AtomicIntegerFieldUpdater<ApiAdmissionPermit> RELEASED_UPDATER =
      AtomicIntegerFieldUpdater.newUpdater(ApiAdmissionPermit.class, "released");
  private static final ReleaseTarget NO_OP_RELEASE = healthy -> {};
  private static final ApiAdmissionPermit IGNORED =
      new ApiAdmissionPermit(true, "", 0, NO_OP_RELEASE);

  private final boolean allowed;
  private final String group;
  private final int retryAfterSeconds;
  private final ReleaseTarget releaseTarget;
  private volatile int released;

  private ApiAdmissionPermit(
      boolean allowed, String group, int retryAfterSeconds, ReleaseTarget releaseTarget) {
    this.allowed = allowed;
    this.group = group;
    this.retryAfterSeconds = retryAfterSeconds;
    this.releaseTarget = releaseTarget;
  }

  static ApiAdmissionPermit acquired(String group, ReleaseTarget releaseTarget) {
    return new ApiAdmissionPermit(true, group, 0, releaseTarget);
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
    if (allowed && RELEASED_UPDATER.compareAndSet(this, 0, 1)) {
      releaseTarget.release(healthy);
    }
  }
}
