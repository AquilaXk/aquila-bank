package com.aquilabank.global.security;

import com.aquilabank.global.web.RequestTraceContext;
import com.aquilabank.standard.util.Sha256Hex;
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

  public void checkTotpChallengeVerify(String ipAddress) {
    check(ipAddress, LoginThrottleEntryPoint.MFA_TOTP_CHALLENGE);
  }

  public void checkBackupCodeChallengeVerify(String ipAddress) {
    check(ipAddress, LoginThrottleEntryPoint.MFA_BACKUP_CODE_CHALLENGE);
  }

  public void checkPasswordRecoveryConfirm(String ipAddress) {
    check(ipAddress, LoginThrottleEntryPoint.PASSWORD_RECOVERY_CONFIRM);
  }

  public void checkRefresh(String ipAddress) {
    check(ipAddress, LoginThrottleEntryPoint.REFRESH);
  }

  private void check(String ipAddress, LoginThrottleEntryPoint entryPoint) {
    String normalizedIpAddress = normalizeIpAddress(ipAddress);
    String throttleKey = entryPoint.keyPrefix() + normalizedIpAddress;
    LoginThrottleStore.ThrottleDecision decision = loginThrottleStore.check(throttleKey);
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
    return Sha256Hex.hashHex(ipAddress);
  }

  private String normalizeIpAddress(String ipAddress) {
    if (ipAddress == null || ipAddress.isBlank()) {
      return "unknown";
    }
    return ipAddress;
  }

  private enum LoginThrottleEntryPoint {
    LOGIN("login", "", "auth login throttled", "too many login attempts"),
    PASSWORD_RECOVERY(
        "password_recovery",
        "password_recovery:",
        "auth password recovery throttled",
        "too many password recovery requests"),
    MFA_TOTP_CHALLENGE(
        "mfa_totp_challenge",
        "mfa_totp_challenge:",
        "auth totp challenge throttled",
        "too many mfa verification attempts"),
    MFA_BACKUP_CODE_CHALLENGE(
        "mfa_backup_code_challenge",
        "mfa_backup_code_challenge:",
        "auth backup code challenge throttled",
        "too many backup code verification attempts"),
    PASSWORD_RECOVERY_CONFIRM(
        "password_recovery_confirm",
        "password_recovery_confirm:",
        "auth password recovery confirm throttled",
        "too many password recovery confirmation attempts"),
    REFRESH(
        "refresh", "refresh:", "auth token refresh throttled", "too many token refresh attempts");

    private final String metricTag;
    private final String keyPrefix;
    private final String logMessage;
    private final String message;

    LoginThrottleEntryPoint(String metricTag, String keyPrefix, String logMessage, String message) {
      this.metricTag = metricTag;
      this.keyPrefix = keyPrefix;
      this.logMessage = logMessage;
      this.message = message;
    }

    String metricTagValue() {
      return metricTag;
    }

    String keyPrefix() {
      return keyPrefix;
    }

    String logMessage() {
      return logMessage;
    }

    String message() {
      return message;
    }
  }
}
