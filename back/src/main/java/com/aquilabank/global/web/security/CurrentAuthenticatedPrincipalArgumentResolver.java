package com.aquilabank.global.web.security;

import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.server.ResponseStatusException;

/** SecurityContext principal을 controller에서 직접 탐색하지 않게 감쌉니다. */
@Component
public class CurrentAuthenticatedPrincipalArgumentResolver
    implements HandlerMethodArgumentResolver {

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    return parameter.hasParameterAnnotation(CurrentAuthenticatedPrincipal.class)
        && AuthenticatedRequestPrincipal.class.isAssignableFrom(parameter.getParameterType());
  }

  @Override
  public Object resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !authentication.isAuthenticated()) {
      throw unauthorized();
    }

    Object principal = authentication.getPrincipal();
    if (principal instanceof AuthenticatedRequestPrincipal requestPrincipal) {
      return requestPrincipal;
    }
    throw unauthorized();
  }

  private ResponseStatusException unauthorized() {
    return new ResponseStatusException(
        org.springframework.http.HttpStatus.UNAUTHORIZED, "authentication required");
  }
}
