package com.aquilabank.global.web.security;

import com.aquilabank.domain.auth.exception.AccountAccessDeniedException;
import com.aquilabank.domain.auth.model.AccountAccessScope;
import com.aquilabank.domain.auth.usecase.AccountAccessUseCase;
import com.aquilabank.global.security.AuthenticatedAccountPrincipal;
import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.web.RequestTraceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 요청에 담긴 accountId를 principal 기준으로 검증해 controller 중복을 줄입니다. */
@Component
public class RequestAccountAuthorizationService {

  private static final Logger log =
      LoggerFactory.getLogger(RequestAccountAuthorizationService.class);

  private final AccountAccessUseCase accountAccessUseCase;

  public RequestAccountAuthorizationService(AccountAccessUseCase accountAccessUseCase) {
    this.accountAccessUseCase = accountAccessUseCase;
  }

  public long resolveReadableAccountId(
      AuthenticatedRequestPrincipal principal, long requestedAccountId) {
    return resolve(principal, requestedAccountId, AccountAccessScope.READ);
  }

  public long resolveTransferSourceAccountId(
      AuthenticatedRequestPrincipal principal, long requestedAccountId) {
    return resolve(principal, requestedAccountId, AccountAccessScope.TRANSFER);
  }

  private long resolve(
      AuthenticatedRequestPrincipal principal, long requestedAccountId, AccountAccessScope scope) {
    if (requestedAccountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }

    if (principal instanceof AuthenticatedUserPrincipal userPrincipal) {
      return accountAccessUseCase.verify(userPrincipal.userId(), requestedAccountId, scope);
    }

    if (principal instanceof AuthenticatedAccountPrincipal accountPrincipal) {
      if (accountPrincipal.accountId() != requestedAccountId) {
        log.warn(
            "bootstrap account mismatch requestId={} subject={} bootstrapAccountId={} requestedAccountId={}",
            RequestTraceContext.currentRequestId().orElse("-"),
            accountPrincipal.subject(),
            accountPrincipal.accountId(),
            requestedAccountId);
        throw new AccountAccessDeniedException("account access is denied");
      }
      return accountAccessUseCase.verifyBootstrapAccount(requestedAccountId, scope);
    }

    throw new IllegalArgumentException("unsupported principal type");
  }
}
