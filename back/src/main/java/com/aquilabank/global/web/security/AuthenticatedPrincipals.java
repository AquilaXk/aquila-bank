package com.aquilabank.global.web.security;

import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 고객 전용 엔드포인트의 인증 주체 검증 공통 헬퍼입니다. */
public final class AuthenticatedPrincipals {

  private AuthenticatedPrincipals() {}

  public static AuthenticatedUserPrincipal requireUserPrincipal(
      AuthenticatedRequestPrincipal principal) {
    if (principal instanceof AuthenticatedUserPrincipal userPrincipal) {
      return userPrincipal;
    }
    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "user authentication required");
  }
}
