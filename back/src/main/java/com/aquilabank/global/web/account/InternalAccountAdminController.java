package com.aquilabank.global.web.account;

import com.aquilabank.domain.account.model.AccountStatus;
import com.aquilabank.domain.account.model.AccountStatusUpdateCommand;
import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.usecase.AccountStatusUpdateUseCase;
import com.aquilabank.global.security.InternalServiceRequestAuthorizer;
import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenClaims;
import com.aquilabank.global.web.RequestTraceContext;
import com.aquilabank.global.web.security.RequestAccountAuthorizationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내부 운영 도구가 bootstrap 이후 계좌 상태를 exact update 하는 전용 adapter입니다. */
@Validated
@RestController
@RequestMapping("/internal/api/v1/accounts")
@ConditionalOnProperty(name = "security.account-bootstrap-api.enabled", havingValue = "true")
public class InternalAccountAdminController {

  private final AccountStatusUpdateUseCase accountStatusUpdateUseCase;
  private final InternalServiceRequestAuthorizer internalServiceRequestAuthorizer;
  private final RequestAccountAuthorizationService requestAccountAuthorizationService;

  public InternalAccountAdminController(
      AccountStatusUpdateUseCase accountStatusUpdateUseCase,
      InternalServiceRequestAuthorizer internalServiceRequestAuthorizer,
      RequestAccountAuthorizationService requestAccountAuthorizationService) {
    this.accountStatusUpdateUseCase = accountStatusUpdateUseCase;
    this.internalServiceRequestAuthorizer = internalServiceRequestAuthorizer;
    this.requestAccountAuthorizationService = requestAccountAuthorizationService;
  }

  @PutMapping("/{accountId}/status")
  public AccountResponse updateStatus(
      HttpServletRequest httpServletRequest,
      @PathVariable @Positive(message = "accountId must be positive") long accountId,
      @Valid @RequestBody AccountStatusRequest request) {
    InternalServiceTokenClaims claims =
        internalServiceRequestAuthorizer.requireScope(
            httpServletRequest, InternalServiceScope.ACCOUNT_ADMIN);
    AccountSummary summary =
        accountStatusUpdateUseCase.update(
            new AccountStatusUpdateCommand(
                accountId,
                request.accountStatus(),
                claims.subject(),
                resolveRequestId(httpServletRequest)));
    requestAccountAuthorizationService.evictAccount(accountId);
    return AccountResponse.from(summary);
  }

  /** 내부 계좌 상태 변경 요청 body */
  public record AccountStatusRequest(
      @NotNull(message = "accountStatus is required") AccountStatus accountStatus) {}

  private String resolveRequestId(HttpServletRequest httpServletRequest) {
    return RequestTraceContext.currentRequestId()
        .orElseGet(
            () -> {
              String requestId =
                  httpServletRequest.getHeader(RequestTraceContext.REQUEST_ID_HEADER);
              if (requestId == null || requestId.isBlank()) {
                throw new IllegalStateException("requestId is not initialized");
              }
              return requestId;
            });
  }

  /** 내부 계좌 상태 변경 응답 */
  public record AccountResponse(
      long accountId,
      String accountNumber,
      String displayName,
      String accountStatus,
      String currencyCode,
      long availableBalanceMinor,
      long pendingBalanceMinor,
      java.time.Instant createdAt,
      java.time.Instant balanceUpdatedAt) {

    static AccountResponse from(AccountSummary summary) {
      return new AccountResponse(
          summary.accountId(),
          summary.accountNumber(),
          summary.displayName(),
          summary.accountStatus(),
          summary.currencyCode(),
          summary.availableBalanceMinor(),
          summary.pendingBalanceMinor(),
          summary.createdAt(),
          summary.balanceUpdatedAt());
    }
  }
}
