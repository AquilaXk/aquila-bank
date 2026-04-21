package com.aquilabank.global.web.auth;

import com.aquilabank.domain.auth.model.ExternalIdentityAuditSummary;
import com.aquilabank.domain.auth.usecase.ExternalIdentityAuditQueryUseCase;
import com.aquilabank.global.security.InternalServiceRequestAuthorizer;
import com.aquilabank.global.security.InternalServiceScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 내부 external identity 감사 requestId exact lookup만 노출합니다. */
@Validated
@RestController
@RequestMapping("/internal/api/v1/auth/external-identity-audits")
@ConditionalOnProperty(name = "security.auth-bootstrap-api.enabled", havingValue = "true")
public class InternalAuthExternalIdentityAuditController {

  private final ExternalIdentityAuditQueryUseCase externalIdentityAuditQueryUseCase;
  private final InternalServiceRequestAuthorizer internalServiceRequestAuthorizer;

  public InternalAuthExternalIdentityAuditController(
      ExternalIdentityAuditQueryUseCase externalIdentityAuditQueryUseCase,
      InternalServiceRequestAuthorizer internalServiceRequestAuthorizer) {
    this.externalIdentityAuditQueryUseCase = externalIdentityAuditQueryUseCase;
    this.internalServiceRequestAuthorizer = internalServiceRequestAuthorizer;
  }

  @GetMapping("/by-request-id")
  public ExternalIdentityAuditResponse getByRequestId(
      HttpServletRequest httpServletRequest,
      @RequestParam @NotBlank(message = "requestId is required") String requestId) {
    internalServiceRequestAuthorizer.requireScope(
        httpServletRequest, InternalServiceScope.AUTH_ADMIN);
    return ExternalIdentityAuditResponse.from(
        externalIdentityAuditQueryUseCase.getByRequestId(requestId));
  }

  /** 내부 external identity audit exact lookup 응답 */
  public record ExternalIdentityAuditResponse(
      String requestId,
      String actorSubject,
      String changeType,
      long userId,
      String providerId,
      String subjectHash,
      String reasonCode,
      String reasonDetail,
      String reason,
      String outcome,
      Instant createdAt) {

    static ExternalIdentityAuditResponse from(ExternalIdentityAuditSummary summary) {
      return new ExternalIdentityAuditResponse(
          summary.requestId(),
          summary.actorSubject(),
          summary.changeType().name(),
          summary.userId(),
          summary.providerId(),
          summary.subjectHash(),
          summary.reasonCode().name(),
          summary.reasonDetail(),
          summary.reasonDetail(),
          summary.outcome().name(),
          summary.createdAt());
    }
  }
}
