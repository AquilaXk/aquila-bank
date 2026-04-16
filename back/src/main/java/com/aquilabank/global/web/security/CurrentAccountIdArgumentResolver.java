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

/** Resolves {@link CurrentAccountId} from the authenticated principal in the security context. */
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
    // Controller code should never inspect SecurityContextHolder directly for account identity.
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
