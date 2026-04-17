package com.aquilabank.global.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** body validation 전에 내부 service JWT를 해석해 request attribute로 고정합니다. */
@Component
public class InternalServiceTokenAuthenticationInterceptor implements HandlerInterceptor {

  private final InternalServiceRequestAuthorizer internalServiceRequestAuthorizer;

  public InternalServiceTokenAuthenticationInterceptor(
      InternalServiceRequestAuthorizer internalServiceRequestAuthorizer) {
    this.internalServiceRequestAuthorizer = internalServiceRequestAuthorizer;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    internalServiceRequestAuthorizer.currentClaims(request);
    return true;
  }
}
