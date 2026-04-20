package com.aquilabank.global.security;

public interface LoginThrottleStore {

  ThrottleDecision check(String ipAddress);

  void clear();

  record ThrottleDecision(
      boolean throttled,
      LoginThrottleScope scope,
      long retryAfterSeconds,
      int maxAttempts,
      long windowSeconds,
      int trackedIpCount) {

    public static ThrottleDecision allowed(
        int maxAttempts, long windowSeconds, int trackedIpCount) {
      return new ThrottleDecision(false, null, 0L, maxAttempts, windowSeconds, trackedIpCount);
    }

    public static ThrottleDecision blocked(
        LoginThrottleScope scope,
        long retryAfterSeconds,
        int maxAttempts,
        long windowSeconds,
        int trackedIpCount) {
      return new ThrottleDecision(
          true, scope, retryAfterSeconds, maxAttempts, windowSeconds, trackedIpCount);
    }
  }
}
