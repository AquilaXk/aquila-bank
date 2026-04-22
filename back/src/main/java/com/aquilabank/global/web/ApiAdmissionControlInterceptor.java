package com.aquilabank.global.web;

import com.aquilabank.global.ops.ApiAdmissionControl;
import com.aquilabank.global.ops.ApiAdmissionPermit;
import com.aquilabank.global.ops.ApiOverloadRejectedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.HandlerInterceptor;

/** permit은 요청 완료/예외 완료 모두 afterCompletion에서 반환합니다. */
public class ApiAdmissionControlInterceptor implements HandlerInterceptor, AsyncHandlerInterceptor {

  private static final String PERMIT_ATTRIBUTE =
      ApiAdmissionControlInterceptor.class.getName() + ".PERMIT";

  private final ApiAdmissionControl admissionControl;

  public ApiAdmissionControlInterceptor(ApiAdmissionControl admissionControl) {
    this.admissionControl = admissionControl;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    ApiAdmissionPermit permit = admissionControl.tryAcquire(request.getRequestURI());
    if (!permit.allowed()) {
      throw new ApiOverloadRejectedException(permit.group(), permit.retryAfterSeconds());
    }
    if (!permit.group().isBlank()) {
      request.setAttribute(PERMIT_ATTRIBUTE, permit);
    }
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
    release(request);
  }

  @Override
  public void afterConcurrentHandlingStarted(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    release(request);
  }

  private void release(HttpServletRequest request) {
    Object value = request.getAttribute(PERMIT_ATTRIBUTE);
    if (value instanceof ApiAdmissionPermit permit) {
      permit.release();
      request.removeAttribute(PERMIT_ATTRIBUTE);
    }
  }
}
