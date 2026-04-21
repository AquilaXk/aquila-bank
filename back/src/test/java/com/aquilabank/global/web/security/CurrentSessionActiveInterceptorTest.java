package com.aquilabank.global.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.CurrentSessionActiveCheckCommand;
import com.aquilabank.domain.auth.usecase.CurrentSessionActiveUseCase;
import com.aquilabank.global.security.AuthenticatedAccountPrincipal;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentSessionActiveInterceptorTest {

  private CurrentSessionActiveUseCase currentSessionActiveUseCase;
  private CurrentSessionActiveInterceptor interceptor;

  @BeforeEach
  void setUp() {
    currentSessionActiveUseCase = mock(CurrentSessionActiveUseCase.class);
    interceptor = new CurrentSessionActiveInterceptor(currentSessionActiveUseCase);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void checksActiveSessionForSensitiveUserMutations() throws Exception {
    authenticate(new AuthenticatedUserPrincipal(7L, "alice", 11L));

    List<RequestPath> items =
        List.of(
            new RequestPath("POST", "/api/v1/transfers"),
            new RequestPath("POST", "/api/v1/transfers/TRX-1/reversal"),
            new RequestPath("POST", "/api/v1/auth/password-reset"),
            new RequestPath("POST", "/api/v1/auth/mfa/totp/enroll"),
            new RequestPath("POST", "/api/v1/auth/mfa/totp/enroll/verify"),
            new RequestPath("POST", "/api/v1/auth/mfa/totp/disable"),
            new RequestPath("POST", "/api/v1/auth/mfa/backup-codes"),
            new RequestPath("DELETE", "/api/v1/auth/sessions"),
            new RequestPath("DELETE", "/api/v1/auth/sessions/11"));

    for (RequestPath item : items) {
      assertTrue(interceptor.preHandle(request(item.method(), item.path()), response(), null));
    }

    verify(currentSessionActiveUseCase, times(items.size()))
        .requireActive(new CurrentSessionActiveCheckCommand(7L, 11L));
  }

  @Test
  void rejectsSensitiveMutationWhenJwtHasNoSessionId() {
    authenticate(new AuthenticatedUserPrincipal(7L, "alice"));

    InvalidCredentialsException exception =
        assertThrows(
            InvalidCredentialsException.class,
            () ->
                interceptor.preHandle(
                    request("POST", "/api/v1/auth/password-reset"), response(), null));

    assertEquals("current session is not active", exception.getMessage());
    verifyNoInteractions(currentSessionActiveUseCase);
  }

  @Test
  void skipsNonSensitiveAuthPathsForLegacyJwt() throws Exception {
    authenticate(new AuthenticatedUserPrincipal(7L, "alice"));

    assertTrue(interceptor.preHandle(request("GET", "/api/v1/auth/sessions"), response(), null));
    assertTrue(interceptor.preHandle(request("POST", "/api/v1/auth/logout"), response(), null));
    assertTrue(
        interceptor.preHandle(
            request("POST", "/api/v1/auth/mfa/totp/challenge/verify"), response(), null));

    verifyNoInteractions(currentSessionActiveUseCase);
  }

  @Test
  void skipsBootstrapPrincipalForExistingControllerAuthorization() throws Exception {
    authenticate(new AuthenticatedAccountPrincipal(101L, "bootstrap-account"));

    assertTrue(interceptor.preHandle(request("POST", "/api/v1/transfers"), response(), null));
    assertTrue(
        interceptor.preHandle(request("POST", "/api/v1/auth/password-reset"), response(), null));

    verifyNoInteractions(currentSessionActiveUseCase);
  }

  private MockHttpServletRequest request(String method, String path) {
    return new MockHttpServletRequest(method, path);
  }

  private MockHttpServletResponse response() {
    return new MockHttpServletResponse();
  }

  private void authenticate(Object principal) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
  }

  private record RequestPath(String method, String path) {}
}
