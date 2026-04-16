package com.aquilabank.global.web.auth;

import com.aquilabank.domain.auth.model.AuthStatusChangeAuditSummary;
import com.aquilabank.domain.auth.usecase.AuthStatusChangeAuditQueryUseCase;
import com.aquilabank.global.security.InternalAuthTokenGuard;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내부 auth 감사 requestId exact lookup만 분리해 운영 조회 경계를 고정합니다. */
@Validated
@RestController
@RequestMapping("/internal/api/v1/auth/status-change-audits")
@ConditionalOnProperty(name = "security.auth-bootstrap-api.enabled", havingValue = "true")
public class InternalAuthStatusChangeAuditController {

  private final AuthStatusChangeAuditQueryUseCase authStatusChangeAuditQueryUseCase;
  private final InternalAuthTokenGuard internalAuthTokenGuard;

  public InternalAuthStatusChangeAuditController(
      AuthStatusChangeAuditQueryUseCase authStatusChangeAuditQueryUseCase,
      InternalAuthTokenGuard internalAuthTokenGuard) {
    this.authStatusChangeAuditQueryUseCase = authStatusChangeAuditQueryUseCase;
    this.internalAuthTokenGuard = internalAuthTokenGuard;
  }

  @GetMapping("/by-request-id")
  public AuthStatusChangeAuditResponse getByRequestId(
      HttpServletRequest httpServletRequest,
      @RequestParam @NotBlank(message = "requestId is required") String requestId) {
    internalAuthTokenGuard.validate(httpServletRequest);
    return AuthStatusChangeAuditResponse.from(
        authStatusChangeAuditQueryUseCase.getByRequestId(requestId));
  }

  /** 내부 auth status change audit exact lookup 응답 */
  public record AuthStatusChangeAuditResponse(
      String requestId,
      String actorSubject,
      long targetUserId,
      Long targetAccountId,
      String changeType,
      String beforeStatus,
      String afterStatus,
      String reason,
      String outcome,
      Instant createdAt) {

    static AuthStatusChangeAuditResponse from(AuthStatusChangeAuditSummary summary) {
      return new AuthStatusChangeAuditResponse(
          summary.requestId(),
          summary.actorSubject(),
          summary.targetUserId(),
          summary.targetAccountId(),
          summary.changeType().name(),
          summary.beforeStatus(),
          summary.afterStatus(),
          summary.reason(),
          summary.outcome().name(),
          summary.createdAt());
    }
  }
}
