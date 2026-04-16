package com.aquilabank.domain.auth.usecase;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.LoginCommand;
import com.aquilabank.domain.auth.model.LoginProtectionPolicy;
import com.aquilabank.domain.auth.port.AuthTokenIssuePort;
import com.aquilabank.domain.auth.port.LoginAttemptAuditPort;
import com.aquilabank.domain.auth.port.LoginAttemptUpdatePort;
import com.aquilabank.domain.auth.port.PasswordHashPort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LoginServiceTest {

  @Test
  void usesDummyHashWhenLoginIdIsMissing() {
    UserCredentialLoadPort userCredentialLoadPort = Mockito.mock(UserCredentialLoadPort.class);
    LoginAttemptUpdatePort loginAttemptUpdatePort = Mockito.mock(LoginAttemptUpdatePort.class);
    LoginAttemptAuditPort loginAttemptAuditPort = Mockito.mock(LoginAttemptAuditPort.class);
    PasswordHashPort passwordHashPort = Mockito.mock(PasswordHashPort.class);
    AuthTokenIssuePort authTokenIssuePort = Mockito.mock(AuthTokenIssuePort.class);

    when(userCredentialLoadPort.findByLoginIdForUpdate("missing-user"))
        .thenReturn(Optional.empty());
    when(passwordHashPort.matches("wrong-password", "dummy-hash")).thenReturn(false);

    LoginService loginService =
        new LoginService(
            userCredentialLoadPort,
            loginAttemptUpdatePort,
            loginAttemptAuditPort,
            passwordHashPort,
            authTokenIssuePort,
            new LoginProtectionPolicy(5, Duration.ofMinutes(15), Duration.ofMinutes(15)),
            "dummy-hash",
            Clock.fixed(Instant.parse("2026-04-17T00:00:00Z"), ZoneOffset.UTC));

    assertThrows(
        InvalidCredentialsException.class,
        () -> loginService.login(new LoginCommand("missing-user", "wrong-password")));

    verify(passwordHashPort).matches("wrong-password", "dummy-hash");
    verify(loginAttemptUpdatePort, never()).recordLoginFailure(Mockito.any());
    verify(loginAttemptUpdatePort, never()).recordLoginSuccess(Mockito.any());
    verify(authTokenIssuePort, never()).issue(Mockito.anyLong(), Mockito.anyString());
    verify(userCredentialLoadPort).findByLoginIdForUpdate(eq("missing-user"));
  }
}
