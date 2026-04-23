package com.aquilabank.global.security;

import com.aquilabank.global.web.RequestTraceContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** login entrypoint 직전에서 IP/global burst를 빠르게 차단합니다. */
public class LoginThrottleGuard {

  private static final Logger log = LoggerFactory.getLogger(LoginThrottleGuard.class);

  private final LoginThrottleStore loginThrottleStore;
  private final LoginThrottlingMetricsRecorder metricsRecorder;

  public LoginThrottleGuard(
      LoginThrottleStore loginThrottleStore, LoginThrottlingMetricsRecorder metricsRecorder) {
    this.loginThrottleStore = loginThrottleStore;
    this.metricsRecorder = metricsRecorder;
  }

  public void check(String ipAddress) {
    check(ipAddress, LoginThrottleEntryPoint.LOGIN);
  }

  public void checkPasswordRecovery(String ipAddress) {
    check(ipAddress, LoginThrottleEntryPoint.PASSWORD_RECOVERY);
  }

  private void check(String ipAddress, LoginThrottleEntryPoint entryPoint) {
    String normalizedIpAddress = normalizeIpAddress(ipAddress);
    LoginThrottleStore.ThrottleDecision decision = loginThrottleStore.check(normalizedIpAddress);
    if (decision.throttled()) {
      metricsRecorder.recordReject(entryPoint.metricTagValue(), decision.scope());
      logThrottle(
          entryPoint,
          decision.scope(),
          normalizedIpAddress,
          decision.retryAfterSeconds(),
          decision.maxAttempts(),
          decision.windowSeconds(),
          decision.trackedIpCount());
      throw new LoginThrottledException(
          decision.scope(), decision.retryAfterSeconds(), entryPoint.message());
    }
  }

  /** 테스트마다 singleton 상태를 비웁니다. */
  public void clear() {
    loginThrottleStore.clear();
  }

  private void logThrottle(
      LoginThrottleEntryPoint entryPoint,
      LoginThrottleScope scope,
      String ipAddress,
      long retryAfterSeconds,
      int maxAttempts,
      long windowSeconds,
      int trackedIpCount) {
    log.warn(
        "{} requestId={} scope={} ipHash={} retryAfterSeconds={} maxAttempts={} windowSeconds={} trackedIpCount={} path={}",
        entryPoint.logMessage(),
        RequestTraceContext.currentRequestId().orElse("-"),
        scope.name(),
        hashIpAddress(ipAddress),
        retryAfterSeconds,
        maxAttempts,
        windowSeconds,
        trackedIpCount,
        currentPath());
  }

  private String currentPath() {
    if (!(RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes)) {
      return "-";
    }
    return attributes.getRequest().getRequestURI();
  }

  private String hashIpAddress(String ipAddress) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(ipAddress.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }

  private String normalizeIpAddress(String ipAddress) {
    if (ipAddress == null || ipAddress.isBlank()) {
      return "unknown";
    }
    return ipAddress;
  }

  private enum LoginThrottleEntryPoint {
    LOGIN("auth login throttled", "too many login attempts"),
    PASSWORD_RECOVERY("auth password recovery throttled", "too many password recovery requests");

    private final String logMessage;
    private final String message;

    LoginThrottleEntryPoint(String logMessage, String message) {
      this.logMessage = logMessage;
      this.message = message;
    }

    String metricTagValue() {
      return this == LOGIN ? "login" : "password_recovery";
    }

    String logMessage() {
      return logMessage;
    }

    String message() {
      return message;
    }
  }
}
