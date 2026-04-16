package com.aquilabank.global.security;

import com.aquilabank.domain.auth.model.LoginFailureAuditEntry;
import com.aquilabank.domain.auth.model.LoginResetAuditEntry;
import com.aquilabank.domain.auth.port.LoginAttemptAuditPort;
import com.aquilabank.global.web.RequestTraceContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** raw loginId 노출 없이 request 단위 structured auth log를 남깁니다. */
public class StructuredLoginAttemptAuditLogger implements LoginAttemptAuditPort {

  private static final Logger log =
      LoggerFactory.getLogger(StructuredLoginAttemptAuditLogger.class);

  @Override
  public void logFailure(LoginFailureAuditEntry entry) {
    log.warn(
        "auth login failed requestId={} loginIdHash={} userId={} failureCount={} remainingAttempts={} lockedUntil={} reason={} path={}",
        RequestTraceContext.currentRequestId().orElse("-"),
        hashLoginId(entry.loginId()),
        entry.userId() == null ? "-" : entry.userId(),
        entry.failureCount(),
        entry.remainingAttempts(),
        entry.lockedUntil() == null ? "-" : entry.lockedUntil(),
        entry.reason().name(),
        currentPath());
  }

  @Override
  public void logReset(LoginResetAuditEntry entry) {
    log.info(
        "auth login failure state reset requestId={} loginIdHash={} userId={} previousFailureCount={} previousLockedUntil={} path={}",
        RequestTraceContext.currentRequestId().orElse("-"),
        hashLoginId(entry.loginId()),
        entry.userId(),
        entry.previousFailureCount(),
        entry.previousLockedUntil() == null ? "-" : entry.previousLockedUntil(),
        currentPath());
  }

  private String currentPath() {
    if (!(RequestContextHolder.getRequestAttributes()
        instanceof ServletRequestAttributes attributes)) {
      return "-";
    }
    return attributes.getRequest().getRequestURI();
  }

  private String hashLoginId(String loginId) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(loginId.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
    }
  }
}
