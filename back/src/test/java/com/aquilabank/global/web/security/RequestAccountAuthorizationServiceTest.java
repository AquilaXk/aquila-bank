package com.aquilabank.global.web.security;

import static org.assertj.core.api.Assertions.assertThat;
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
  void handlesNanoTimeWrapAroundWithoutOverflowBug() {
    AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(10L, "user-10");
    when(accountAccessUseCase.verify(10L, 101L, AccountAccessScope.READ)).thenReturn(101L);

    nowNanos.set(Long.MAX_VALUE - Duration.ofMillis(100).toNanos());
    service.resolveReadableAccountId(principal, 101L);

    // Advance 200ms across Long.MAX_VALUE wrap-around boundary (still within 500ms TTL)
    nowNanos.addAndGet(Duration.ofMillis(200).toNanos());
    service.resolveReadableAccountId(principal, 101L);

    verify(accountAccessUseCase, times(1)).verify(10L, 101L, AccountAccessScope.READ);

    // Advance another 400ms (total 600ms > 500ms TTL)
    nowNanos.addAndGet(Duration.ofMillis(400).toNanos());
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
  void evictsUserAuthorizationCacheByUserId() {
    AuthenticatedUserPrincipal principal10 = new AuthenticatedUserPrincipal(10L, "user-10");
    AuthenticatedUserPrincipal principal20 = new AuthenticatedUserPrincipal(20L, "user-20");
    when(accountAccessUseCase.verify(10L, 101L, AccountAccessScope.READ)).thenReturn(101L);
    when(accountAccessUseCase.verify(20L, 102L, AccountAccessScope.READ)).thenReturn(102L);

    service.resolveReadableAccountId(principal10, 101L);
    service.resolveReadableAccountId(principal20, 102L);

    service.evictUser(10L);
    service.evictUser(0L);

    service.resolveReadableAccountId(principal10, 101L);
    service.resolveReadableAccountId(principal20, 102L);

    verify(accountAccessUseCase, times(2)).verify(10L, 101L, AccountAccessScope.READ);
    verify(accountAccessUseCase, times(1)).verify(20L, 102L, AccountAccessScope.READ);
  }

  @Test
  void boundsCacheSizeAndPrunesExpiredOrExcessEntries() {
    for (long i = 1; i <= 4096; i++) {
      AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(i, "user-" + i);
      when(accountAccessUseCase.verify(i, 100L + i, AccountAccessScope.READ)).thenReturn(100L + i);
      service.resolveReadableAccountId(principal, 100L + i);
    }

    // Adding one more when none are expired clears/bounds the cache and stores the new entry
    AuthenticatedUserPrincipal extraPrincipal = new AuthenticatedUserPrincipal(9999L, "user-9999");
    when(accountAccessUseCase.verify(9999L, 9999L, AccountAccessScope.READ)).thenReturn(9999L);
    assertThat(service.resolveReadableAccountId(extraPrincipal, 9999L)).isEqualTo(9999L);

    // Fill again and expire before adding 4097th entry so expired pruning path runs
    for (long i = 1; i <= 4095; i++) {
      AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(i, "user-" + i);
      service.resolveReadableAccountId(principal, 100L + i);
    }
    nowNanos.addAndGet(Duration.ofSeconds(1).toNanos());
    assertThat(service.resolveReadableAccountId(extraPrincipal, 9999L)).isEqualTo(9999L);
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

  @Test
  void handlesTransferSourceAuthorizationAndInvalidPrincipalOrAccountIds() {
    RequestAccountAuthorizationService defaultService =
        new RequestAccountAuthorizationService(accountAccessUseCase);
    AuthenticatedUserPrincipal userPrincipal = new AuthenticatedUserPrincipal(10L, "user-10");
    AuthenticatedAccountPrincipal accountPrincipal =
        new AuthenticatedAccountPrincipal(101L, "bootstrap");

    when(accountAccessUseCase.verify(10L, 101L, AccountAccessScope.TRANSFER)).thenReturn(101L);
    when(accountAccessUseCase.verifyBootstrapAccount(101L, AccountAccessScope.TRANSFER))
        .thenReturn(101L);

    assertThat(defaultService.resolveTransferSourceAccountId(userPrincipal, 101L)).isEqualTo(101L);
    assertThat(defaultService.resolveTransferSourceAccountId(accountPrincipal, 101L))
        .isEqualTo(101L);

    assertThatThrownBy(() -> defaultService.resolveReadableAccountId(null, 101L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> defaultService.resolveReadableAccountId(userPrincipal, 0L))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> defaultService.resolveReadableAccountId(accountPrincipal, 999L))
        .isInstanceOf(AccountAccessDeniedException.class);
  }
}
