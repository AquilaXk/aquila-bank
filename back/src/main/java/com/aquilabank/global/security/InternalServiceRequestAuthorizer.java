package com.aquilabank.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** interceptor가 심은 내부 service JWT claim을 endpoint scope 기준으로 재검증합니다. */
@Component
public class InternalServiceRequestAuthorizer {

  public static final String REQUEST_ATTRIBUTE =
      InternalServiceRequestAuthorizer.class.getName() + ".claims";

  private final InternalServiceTokenVerifier verifier;

  public InternalServiceRequestAuthorizer(InternalServiceTokenVerifier verifier) {
    this.verifier = verifier;
  }

  public InternalServiceTokenClaims requireScope(
      HttpServletRequest request, InternalServiceScope scope) {
    InternalServiceTokenClaims claims = currentClaims(request);
    if (!claims.hasScope(scope)) {
      throw new BootstrapApiAccessDeniedException("internal service token is invalid");
    }
    return claims;
  }

  public InternalServiceTokenClaims currentClaims(HttpServletRequest request) {
    Object value = request.getAttribute(REQUEST_ATTRIBUTE);
    if (value instanceof InternalServiceTokenClaims claims) {
      return claims;
    }
    InternalServiceTokenClaims claims = verifier.verify(request);
    request.setAttribute(REQUEST_ATTRIBUTE, claims);
    return claims;
  }
}
