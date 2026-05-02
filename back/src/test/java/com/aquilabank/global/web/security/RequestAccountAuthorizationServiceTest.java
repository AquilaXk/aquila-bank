package com.aquilabank.global.web.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.exception.AccountAccessDeniedException;
import com.aquilabank.domain.auth.model.AccountAccessScope;
import com.aquilabank.domain.auth.usecase.AccountAccessUseCase;
import com.aquilabank.global.security.AuthenticatedAccountPrincipal;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class RequestAccountAuthorizationServiceTest {

  private final AccountAccessUseCase accountAccessUseCase = mock(AccountAccessUseCase.class);
  private final AtomicLong nowNanos = new AtomicLong(Duration.ofSeconds(10).toNanos());
  private final RequestAccountAuthorizationService service =
      new RequestAccountAuthorizationService(
          accountAccessUseCase, Duration.ofMillis(500), nowNanos::get);

  @Test
  void cachesSuccessfulUserAuthorizationWithinShortTtl() {
    AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(10L, "user-10");
    when(accountAccessUseCase.verify(10L, 101L, AccountAccessScope.READ)).thenReturn(101L);

    service.resolveReadableAccountId(principal, 101L);
    service.resolveReadableAccountId(principal, 101L);

    verify(accountAccessUseCase, times(1)).verify(10L, 101L, AccountAccessScope.READ);
  }

  @Test
  void expiresSuccessfulAuthorizationAfterTtl() {
    AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(10L, "user-10");
    when(accountAccessUseCase.verify(10L, 101L, AccountAccessScope.READ)).thenReturn(101L);

    service.resolveReadableAccountId(principal, 101L);
    nowNanos.addAndGet(Duration.ofMillis(501).toNanos());
    service.resolveReadableAccountId(principal, 101L);

    verify(accountAccessUseCase, times(2)).verify(10L, 101L, AccountAccessScope.READ);
  }

  @Test
  void evictsAccountAuthorizationCacheByAccountId() {
    AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(10L, "user-10");
    when(accountAccessUseCase.verify(10L, 101L, AccountAccessScope.READ)).thenReturn(101L);

    service.resolveReadableAccountId(principal, 101L);
    service.evictAccount(101L);
    service.resolveReadableAccountId(principal, 101L);

    verify(accountAccessUseCase, times(2)).verify(10L, 101L, AccountAccessScope.READ);
  }

  @Test
  void ignoresInvalidEvictionKey() {
    service.evictAccount(0L);

    verifyNoInteractions(accountAccessUseCase);
  }

  @Test
  void doesNotCacheDeniedAuthorization() {
    AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(10L, "user-10");
    when(accountAccessUseCase.verify(10L, 101L, AccountAccessScope.READ))
        .thenThrow(new AccountAccessDeniedException("account access is denied"));

    assertThatThrownBy(() -> service.resolveReadableAccountId(principal, 101L))
        .isInstanceOf(AccountAccessDeniedException.class);
    assertThatThrownBy(() -> service.resolveReadableAccountId(principal, 101L))
        .isInstanceOf(AccountAccessDeniedException.class);

    verify(accountAccessUseCase, times(2)).verify(10L, 101L, AccountAccessScope.READ);
  }

  @Test
  void doesNotCacheBootstrapAuthorizationBecauseStatusMustStayExact() {
    AuthenticatedAccountPrincipal principal = new AuthenticatedAccountPrincipal(101L, "bootstrap");
    when(accountAccessUseCase.verifyBootstrapAccount(101L, AccountAccessScope.READ))
        .thenReturn(101L);

    service.resolveReadableAccountId(principal, 101L);
    service.resolveReadableAccountId(principal, 101L);

    verify(accountAccessUseCase, times(2)).verifyBootstrapAccount(101L, AccountAccessScope.READ);
  }
}
