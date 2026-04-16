package com.aquilabank.global.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aquilabank.global.security.AuthenticatedAccountPrincipal;
import java.lang.reflect.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.server.ResponseStatusException;

class CurrentAccountIdArgumentResolverTest {

  private final CurrentAccountIdArgumentResolver resolver = new CurrentAccountIdArgumentResolver();

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void resolvesAccountIdFromPrincipal() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedAccountPrincipal(321L, "tester"), null, java.util.List.of()));

    Object resolved =
        resolver.resolveArgument(
            parameter(), null, new ServletWebRequest(new MockHttpServletRequest()), null);

    assertEquals(321L, resolved);
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
    Method method = Fixture.class.getDeclaredMethod("handle", long.class);
    return new MethodParameter(method, 0);
  }

  private static final class Fixture {

    void handle(@CurrentAccountId long accountId) {}
  }
}
