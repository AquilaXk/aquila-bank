package com.aquilabank.global.web.security;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.CurrentSessionActiveAuditEvent;
import com.aquilabank.domain.auth.model.CurrentSessionActiveCheckCommand;
import com.aquilabank.domain.auth.model.CurrentSessionActiveRejectReason;
import com.aquilabank.domain.auth.port.CurrentSessionActiveAuditPort;
import com.aquilabank.domain.auth.usecase.CurrentSessionActiveUseCase;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.web.RequestTraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** 민감 mutation에서 JWT session_id가 아직 ACTIVE인지 재확인합니다. */
@Component
public class CurrentSessionActiveInterceptor implements HandlerInterceptor {

  private static final String CURRENT_SESSION_INACTIVE_MESSAGE = "current session is not active";
  private static final String DELETE_EXACT_PATH = "/api/v1/auth/sessions";
  private static final Set<String> POST_EXACT_PATHS =
      Set.of(
          "/api/v1/transfers",
          "/api/v1/auth/password-reset",
          "/api/v1/auth/mfa/totp/enroll",
          "/api/v1/auth/mfa/totp/enroll/verify",
          "/api/v1/auth/mfa/totp/disable",
          "/api/v1/auth/mfa/backup-codes",
          "/api/v1/customer-service/applications",
          "/api/v1/notifications/preferences",
          "/api/v1/notifications/read",
          "/api/v1/notifications/archive",
          "/api/v1/notifications/delete");

  private final CurrentSessionActiveUseCase currentSessionActiveUseCase;
  private final CurrentSessionActiveAuditPort currentSessionActiveAuditPort;

  public CurrentSessionActiveInterceptor(
      CurrentSessionActiveUseCase currentSessionActiveUseCase,
      CurrentSessionActiveAuditPort currentSessionActiveAuditPort) {
    this.currentSessionActiveUseCase = currentSessionActiveUseCase;
    this.currentSessionActiveAuditPort = currentSessionActiveAuditPort;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    String method = request.getMethod();
    String path = normalizedPath(request);
    if (!isSensitiveMutation(method, path)) {
      return true;
    }

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      return true;
    }

    Object principal = authentication.getPrincipal();
    if (!(principal instanceof AuthenticatedUserPrincipal userPrincipal)) {
      return true;
    }

    Long currentSessionId = userPrincipal.currentSessionId();
    if (currentSessionId == null) {
      // session_id 없는 구 access token은 read 호환만 허용하고 고위험 mutation은 재로그인 요구.
      currentSessionActiveAuditPort.record(
          new CurrentSessionActiveAuditEvent(
              requestId(),
              userPrincipal.userId(),
              null,
              method,
              path,
              CurrentSessionActiveRejectReason.MISSING_SESSION_ID,
              Instant.now()));
      throw new InvalidCredentialsException(CURRENT_SESSION_INACTIVE_MESSAGE);
    }

    currentSessionActiveUseCase.requireActive(
        new CurrentSessionActiveCheckCommand(
            userPrincipal.userId(), currentSessionId, requestId(), method, path));
    return true;
  }

  private boolean isSensitiveMutation(String method, String path) {
    if ("POST".equals(method)) {
      return POST_EXACT_PATHS.contains(path)
          || matchesSingleSegmentTemplate(path, "/api/v1/transfers/", "/reversal")
          || matchesSingleSegmentTemplate(path, "/api/v1/customer-service/applications/", "/cancel")
          || matchesSingleSegmentTemplate(path, "/api/v1/notifications/", "/read");
    }
    if ("DELETE".equals(method)) {
      return DELETE_EXACT_PATH.equals(path)
          || matchesSingleSegmentPrefix(path, "/api/v1/auth/sessions/");
    }
    return false;
  }

  private static boolean matchesSingleSegmentTemplate(String path, String prefix, String suffix) {
    int minLength = prefix.length() + suffix.length() + 1;
    if (path.length() < minLength || !path.startsWith(prefix) || !path.endsWith(suffix)) {
      return false;
    }
    int segmentEnd = path.length() - suffix.length();
    return path.indexOf('/', prefix.length()) == segmentEnd;
  }

  private static boolean matchesSingleSegmentPrefix(String path, String prefix) {
    return path.length() > prefix.length()
        && path.startsWith(prefix)
        && path.indexOf('/', prefix.length()) < 0;
  }

  private String requestId() {
    return RequestTraceContext.currentRequestId().orElse("-");
  }

  private String normalizedPath(HttpServletRequest request) {
    String path = request.getRequestURI();
    String contextPath = request.getContextPath();
    if (contextPath != null && !contextPath.isBlank() && path.startsWith(contextPath)) {
      path = path.substring(contextPath.length());
    }
    return path.isBlank() ? "/" : path;
  }
}
