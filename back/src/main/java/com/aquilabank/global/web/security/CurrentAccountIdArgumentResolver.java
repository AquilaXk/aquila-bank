package com.aquilabank.global.web.security;

import com.aquilabank.global.security.AuthenticatedAccountPrincipal;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

/** SecurityContext principal에서 {@link CurrentAccountId}를 해석하는 resolver */
@Component
public class CurrentAccountIdArgumentResolver implements HandlerMethodArgumentResolver {

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(CurrentAccountId.class)
        && (parameter.getParameterType().equals(long.class)
            || parameter.getParameterType().equals(Long.class));
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    // controller에서 SecurityContext 직접 탐색 제거
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      throw unauthorized();
    }
    Object principal = authentication.getPrincipal();
    if (principal instanceof AuthenticatedAccountPrincipal accountPrincipal) {
      return accountPrincipal.accountId();
    }
    throw unauthorized();
  }

  private ResponseStatusException unauthorized() {
    return new ResponseStatusException(
        org.springframework.http.HttpStatus.UNAUTHORIZED, "authentication required");
  }
}
