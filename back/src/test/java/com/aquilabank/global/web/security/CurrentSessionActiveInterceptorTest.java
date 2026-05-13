package com.aquilabank.global.web.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.CurrentSessionActiveAuditEvent;
import com.aquilabank.domain.auth.model.CurrentSessionActiveCheckCommand;
import com.aquilabank.domain.auth.model.CurrentSessionActiveRejectReason;
import com.aquilabank.domain.auth.port.CurrentSessionActiveAuditPort;
import com.aquilabank.domain.auth.usecase.CurrentSessionActiveUseCase;
import com.aquilabank.global.security.AuthenticatedAccountPrincipal;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.web.RequestTraceContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentSessionActiveInterceptorTest {

  private CurrentSessionActiveUseCase currentSessionActiveUseCase;
  private CurrentSessionActiveAuditPort currentSessionActiveAuditPort;
  private CurrentSessionActiveInterceptor interceptor;

  @BeforeEach
  void setUp() {
    currentSessionActiveUseCase = mock(CurrentSessionActiveUseCase.class);
    currentSessionActiveAuditPort = mock(CurrentSessionActiveAuditPort.class);
    interceptor =
        new CurrentSessionActiveInterceptor(
            currentSessionActiveUseCase, currentSessionActiveAuditPort);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
    RequestTraceContext.clear();
  }

  @Test
  void checksActiveSessionForSensitiveUserMutations() throws Exception {
    authenticate(new AuthenticatedUserPrincipal(7L, "alice", 11L));
    RequestTraceContext.set("req-active");

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
            new RequestPath("DELETE", "/api/v1/auth/sessions/11"),
            new RequestPath("POST", "/api/v1/customer-service/applications"),
            new RequestPath("POST", "/api/v1/notifications/preferences"),
            new RequestPath("POST", "/api/v1/notifications/10/read"),
            new RequestPath("POST", "/api/v1/notifications/read"),
            new RequestPath("POST", "/api/v1/notifications/archive"),
            new RequestPath("POST", "/api/v1/notifications/delete"));

    for (RequestPath item : items) {
      assertTrue(interceptor.preHandle(request(item.method(), item.path()), response(), null));
    }

    ArgumentCaptor<CurrentSessionActiveCheckCommand> commandCaptor =
        ArgumentCaptor.forClass(CurrentSessionActiveCheckCommand.class);
    verify(currentSessionActiveUseCase, times(items.size())).requireActive(commandCaptor.capture());
    List<CurrentSessionActiveCheckCommand> commands = commandCaptor.getAllValues();
    assertEquals(items.size(), commands.size());
    for (int i = 0; i < items.size(); i++) {
      CurrentSessionActiveCheckCommand command = commands.get(i);
      RequestPath item = items.get(i);
      assertEquals(7L, command.userId());
      assertEquals(11L, command.sessionId());
      assertEquals("req-active", command.requestId());
      assertEquals(item.method(), command.method());
      assertEquals(item.path(), command.path());
    }
    verifyNoInteractions(currentSessionActiveAuditPort);
  }

  @Test
  void recordsAuditEventWhenSensitiveMutationJwtHasNoSessionId() {
    authenticate(new AuthenticatedUserPrincipal(7L, "alice"));
    RequestTraceContext.set("req-missing");

    InvalidCredentialsException exception =
        assertThrows(
            InvalidCredentialsException.class,
            () ->
                interceptor.preHandle(
                    request("POST", "/api/v1/auth/password-reset"), response(), null));

    assertEquals("current session is not active", exception.getMessage());
    verifyNoInteractions(currentSessionActiveUseCase);
    ArgumentCaptor<CurrentSessionActiveAuditEvent> eventCaptor =
        ArgumentCaptor.forClass(CurrentSessionActiveAuditEvent.class);
    verify(currentSessionActiveAuditPort).record(eventCaptor.capture());
    CurrentSessionActiveAuditEvent event = eventCaptor.getValue();
    assertEquals("req-missing", event.requestId());
    assertEquals(7L, event.userId());
    assertEquals(null, event.sessionId());
    assertEquals("POST", event.method());
    assertEquals("/api/v1/auth/password-reset", event.path());
    assertEquals(CurrentSessionActiveRejectReason.MISSING_SESSION_ID, event.reasonCode());
    assertTrue(event.occurredAt() != null);
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
    verifyNoInteractions(currentSessionActiveAuditPort);
  }

  @Test
  void skipsBootstrapPrincipalForExistingControllerAuthorization() throws Exception {
    authenticate(new AuthenticatedAccountPrincipal(101L, "bootstrap-account"));

    assertTrue(interceptor.preHandle(request("POST", "/api/v1/transfers"), response(), null));
    assertTrue(
        interceptor.preHandle(request("POST", "/api/v1/auth/password-reset"), response(), null));

    verifyNoInteractions(currentSessionActiveUseCase);
    verifyNoInteractions(currentSessionActiveAuditPort);
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
