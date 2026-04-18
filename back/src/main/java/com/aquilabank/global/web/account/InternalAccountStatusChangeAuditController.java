package com.aquilabank.global.web.account;

import com.aquilabank.domain.account.model.AccountStatusChangeAuditSummary;
import com.aquilabank.domain.account.usecase.AccountStatusChangeAuditQueryUseCase;
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

/** 내부 계좌 상태 변경 감사 requestId exact lookup만 분리해 운영 조회 경계를 고정합니다. */
@Validated
@RestController
@RequestMapping("/internal/api/v1/accounts/status-change-audits")
@ConditionalOnProperty(name = "security.account-bootstrap-api.enabled", havingValue = "true")
public class InternalAccountStatusChangeAuditController {

  private final AccountStatusChangeAuditQueryUseCase accountStatusChangeAuditQueryUseCase;
  private final InternalServiceRequestAuthorizer internalServiceRequestAuthorizer;

  public InternalAccountStatusChangeAuditController(
      AccountStatusChangeAuditQueryUseCase accountStatusChangeAuditQueryUseCase,
      InternalServiceRequestAuthorizer internalServiceRequestAuthorizer) {
    this.accountStatusChangeAuditQueryUseCase = accountStatusChangeAuditQueryUseCase;
    this.internalServiceRequestAuthorizer = internalServiceRequestAuthorizer;
  }

  @GetMapping("/by-request-id")
  public AccountStatusChangeAuditResponse getByRequestId(
      HttpServletRequest httpServletRequest,
      @RequestParam @NotBlank(message = "requestId is required") String requestId) {
    internalServiceRequestAuthorizer.requireScope(
        httpServletRequest, InternalServiceScope.ACCOUNT_ADMIN);
    return AccountStatusChangeAuditResponse.from(
        accountStatusChangeAuditQueryUseCase.getByRequestId(requestId));
  }

  /** 내부 계좌 상태 변경 감사 exact lookup 응답 */
  public record AccountStatusChangeAuditResponse(
      String requestId,
      String actorSubject,
      long targetAccountId,
      String beforeStatus,
      String afterStatus,
      Instant createdAt) {

    static AccountStatusChangeAuditResponse from(AccountStatusChangeAuditSummary summary) {
      return new AccountStatusChangeAuditResponse(
          summary.requestId(),
          summary.actorSubject(),
          summary.targetAccountId(),
          summary.beforeStatus(),
          summary.afterStatus(),
          summary.createdAt());
    }
  }
}
