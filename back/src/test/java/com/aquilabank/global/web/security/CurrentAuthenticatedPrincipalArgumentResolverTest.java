package com.aquilabank.global.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

class CurrentAuthenticatedPrincipalArgumentResolverTest {

  private final CurrentAuthenticatedPrincipalArgumentResolver resolver =
      new CurrentAuthenticatedPrincipalArgumentResolver();

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void resolvesPrincipalFromSecurityContext() throws Exception {
    AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(321L, "tester");
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, java.util.List.of()));

    Object resolved =
        resolver.resolveArgument(
            parameter(), null, new ServletWebRequest(new MockHttpServletRequest()), null);

    assertEquals(principal, resolved);
  }

  @Test
  void rejectsMissingAuthentication() throws Exception {
    assertThrows(
        ResponseStatusException.class,
        () ->
            resolver.resolveArgument(
                parameter(), null, new ServletWebRequest(new MockHttpServletRequest()), null));
  }

  private MethodParameter parameter() throws NoSuchMethodException {
    Method method =
        Fixture.class.getDeclaredMethod(
            "handle", com.aquilabank.global.security.AuthenticatedRequestPrincipal.class);
    return new MethodParameter(method, 0);
  }

  private static final class Fixture {

    void handle(
        @CurrentAuthenticatedPrincipal
            com.aquilabank.global.security.AuthenticatedRequestPrincipal principal) {}
  }
}
