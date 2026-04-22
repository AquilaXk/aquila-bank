package com.aquilabank.global.web;

import com.aquilabank.global.ops.T3MicroSaturationDecision;
import com.aquilabank.global.ops.T3MicroSaturationGuard;
import com.aquilabank.global.ops.T3MicroSaturationRejectedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/** DB 의존 API가 runtime 포화 상태를 더 키우지 않도록 controller 진입 전에 차단합니다. */
public class T3MicroSaturationInterceptor implements HandlerInterceptor {

  private final T3MicroSaturationGuard guard;

  public T3MicroSaturationInterceptor(T3MicroSaturationGuard guard) {
    this.guard = guard;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    T3MicroSaturationDecision decision = guard.check(request.getRequestURI());
    if (decision.allowed()) {
      return true;
    }
    throw new T3MicroSaturationRejectedException(decision.retryAfterSeconds());
  }
}
