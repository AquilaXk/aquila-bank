package com.aquilabank.global.web.account;

import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.usecase.AccountSummaryQueryUseCase;
import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipal;
import com.aquilabank.global.web.security.RequestAccountAuthorizationService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 고객 계좌 요약 단건 조회를 노출하는 HTTP adapter */
@Validated
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountSummaryController {

  private final AccountSummaryQueryUseCase accountSummaryQueryUseCase;
  private final RequestAccountAuthorizationService requestAccountAuthorizationService;

  public AccountSummaryController(
      AccountSummaryQueryUseCase accountSummaryQueryUseCase,
      RequestAccountAuthorizationService requestAccountAuthorizationService) {
    this.accountSummaryQueryUseCase = accountSummaryQueryUseCase;
    this.requestAccountAuthorizationService = requestAccountAuthorizationService;
  }

  @GetMapping("/{accountId}")
  public AccountSummaryResponse getAccount(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @PathVariable @Positive(message = "accountId must be positive") long accountId) {
    try {
      // 권한 실패를 먼저 끊어 타 계좌 존재 여부를 요약 조회 응답으로 노출하지 않습니다.
      long resolvedAccountId =
          requestAccountAuthorizationService.resolveReadableAccountId(principal, accountId);
      return AccountSummaryResponse.from(
          accountSummaryQueryUseCase.getByAccountId(resolvedAccountId));
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }
  }

  /** 고객 계좌 요약 응답 */
  public record AccountSummaryResponse(
      long accountId,
      String accountNumber,
      String displayName,
      String accountStatus,
      String currencyCode,
      long availableBalanceMinor,
      long pendingBalanceMinor,
      java.time.Instant createdAt,
      java.time.Instant balanceUpdatedAt) {

    static AccountSummaryResponse from(AccountSummary summary) {
      return new AccountSummaryResponse(
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
