package com.aquilabank.global.web.security;

import com.aquilabank.domain.auth.exception.AccountAccessDeniedException;
import com.aquilabank.domain.auth.model.AccountAccessScope;
import com.aquilabank.domain.auth.usecase.AccountAccessUseCase;
import com.aquilabank.global.security.AuthenticatedAccountPrincipal;
import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.web.RequestTraceContext;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 요청에 담긴 accountId를 principal 기준으로 검증해 controller 중복을 줄입니다. */
@Component
public class RequestAccountAuthorizationService {

  private static final Logger log =
      LoggerFactory.getLogger(RequestAccountAuthorizationService.class);
  private static final Duration AUTHORIZATION_CACHE_TTL = Duration.ofMillis(500);

  private final AccountAccessUseCase accountAccessUseCase;
  // 성공한 account access만 짧게 재사용해 hot read의 반복 DB 왕복을 줄입니다.
  private final ConcurrentHashMap<AuthorizationCacheKey, AuthorizationCacheEntry>
      authorizationCache = new ConcurrentHashMap<>();
  private final long authorizationCacheTtlNanos;
  private final LongSupplier nanoTime;

  @Autowired
  public RequestAccountAuthorizationService(AccountAccessUseCase accountAccessUseCase) {
    this(accountAccessUseCase, AUTHORIZATION_CACHE_TTL, System::nanoTime);
  }

  RequestAccountAuthorizationService(
      AccountAccessUseCase accountAccessUseCase,
      Duration authorizationCacheTtl,
      LongSupplier nanoTime) {
    this.accountAccessUseCase = Objects.requireNonNull(accountAccessUseCase);
    this.authorizationCacheTtlNanos = Objects.requireNonNull(authorizationCacheTtl).toNanos();
    this.nanoTime = Objects.requireNonNull(nanoTime);
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
      return resolveCached(
          new AuthorizationCacheKey(userPrincipal.userId(), requestedAccountId, scope),
          () -> accountAccessUseCase.verify(userPrincipal.userId(), requestedAccountId, scope));
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
      // bootstrap principal은 account status exact 확인이 목적이라 cache하지 않습니다.
      return accountAccessUseCase.verifyBootstrapAccount(requestedAccountId, scope);
    }

    throw new IllegalArgumentException("unsupported principal type");
  }

  public void evictAccount(long accountId) {
    if (accountId <= 0) {
      return;
    }
    // 상태 변경 직후 오래된 권한 cache가 남지 않도록 account 단위로 비웁니다.
    authorizationCache.keySet().removeIf(key -> key.accountId() == accountId);
  }

  private long resolveCached(AuthorizationCacheKey key, LongSupplier accountIdResolver) {
    long nowNanos = nanoTime.getAsLong();
    AuthorizationCacheEntry cached = authorizationCache.get(key);
    if (cached != null && cached.isFresh(nowNanos)) {
      return cached.resolvedAccountId();
    }
    if (cached != null) {
      authorizationCache.remove(key, cached);
    }
    long resolvedAccountId = accountIdResolver.getAsLong();
    authorizationCache.put(
        key, new AuthorizationCacheEntry(resolvedAccountId, nowNanos + authorizationCacheTtlNanos));
    return resolvedAccountId;
  }

  private record AuthorizationCacheKey(long userId, long accountId, AccountAccessScope scope) {}

  private record AuthorizationCacheEntry(long resolvedAccountId, long expiresAtNanos) {

    boolean isFresh(long nowNanos) {
      return nowNanos < expiresAtNanos;
    }
  }
}
