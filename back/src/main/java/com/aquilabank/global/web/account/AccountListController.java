package com.aquilabank.global.web.account;

import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.model.AccountSummaryList;
import com.aquilabank.domain.account.usecase.AccountListQueryUseCase;
import com.aquilabank.domain.account.usecase.AccountSummaryQueryUseCase;
import com.aquilabank.global.security.AuthenticatedAccountPrincipal;
import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipal;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 고객 계좌 목록 조회를 principal 종류에 맞춰 노출하는 HTTP adapter */
@Validated
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountListController {

  private static final int DEFAULT_PAGE_LIMIT = 50;

  private final AccountListQueryUseCase accountListQueryUseCase;
  private final AccountSummaryQueryUseCase accountSummaryQueryUseCase;

  public AccountListController(
      AccountListQueryUseCase accountListQueryUseCase,
      AccountSummaryQueryUseCase accountSummaryQueryUseCase) {
    this.accountListQueryUseCase = accountListQueryUseCase;
    this.accountSummaryQueryUseCase = accountSummaryQueryUseCase;
  }

  @GetMapping
  public AccountListResponse getAccounts(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @RequestParam(required = false) Integer limit,
      @RequestParam(required = false) String cursor) {
    try {
      return AccountListResponse.from(resolveAccountSummaryList(principal, limit, cursor));
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }
  }

  private AccountSummaryList resolveAccountSummaryList(
      AuthenticatedRequestPrincipal principal, Integer limit, String cursor) {
    if (principal instanceof AuthenticatedUserPrincipal userPrincipal) {
      if (limit != null || hasCursor(cursor)) {
        int resolvedLimit = limit == null ? DEFAULT_PAGE_LIMIT : limit;
        Long afterAccountId = hasCursor(cursor) ? AccountListCursorCodec.decode(cursor) : null;
        return accountListQueryUseCase.getByUserId(
            userPrincipal.userId(), resolvedLimit, afterAccountId);
      }
      return accountListQueryUseCase.getByUserId(userPrincipal.userId());
    }
    if (principal instanceof AuthenticatedAccountPrincipal accountPrincipal) {
      // bootstrap account principal도 같은 요약 모델을 재사용해 dev/test 응답 계약을 단순화합니다.
      return new AccountSummaryList(
          List.of(accountSummaryQueryUseCase.getByAccountId(accountPrincipal.accountId())));
    }
    throw new IllegalArgumentException("unsupported principal type");
  }

  private boolean hasCursor(String cursor) {
    return cursor != null && !cursor.isBlank();
  }

  /** 고객 계좌 목록 응답 */
  public record AccountListResponse(List<AccountItemResponse> items, String nextCursor) {

    static AccountListResponse from(AccountSummaryList summaryList) {
      return new AccountListResponse(
          summaryList.items().stream().map(AccountItemResponse::from).toList(),
          summaryList.nextCursorAccountId() == null
              ? null
              : AccountListCursorCodec.encode(summaryList.nextCursorAccountId()));
    }
  }

  /** 고객 계좌 목록 item 응답 */
  public record AccountItemResponse(
      long accountId,
      String accountNumber,
      String displayName,
      String accountStatus,
      String currencyCode,
      long availableBalanceMinor,
      long pendingBalanceMinor,
      Instant createdAt,
      Instant balanceUpdatedAt) {

    static AccountItemResponse from(AccountSummary summary) {
      return new AccountItemResponse(
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
