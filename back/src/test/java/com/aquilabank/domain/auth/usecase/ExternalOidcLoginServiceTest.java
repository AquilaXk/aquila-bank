package com.aquilabank.domain.auth.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.AuthSessionClientMetadata;
import com.aquilabank.domain.auth.model.ExternalOidcLoginCommand;
import com.aquilabank.domain.auth.model.IssuedAccessToken;
import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.model.LoginResultStatus;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.RefreshTokenPolicy;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.AuthTokenIssuePort;
import com.aquilabank.domain.auth.port.ExternalIdentityUserLoadPort;
import com.aquilabank.domain.auth.port.LoginAttemptAuditPort;
import com.aquilabank.domain.auth.port.LoginAttemptUpdatePort;
import com.aquilabank.domain.auth.port.RefreshDeviceBindingSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSecretPort;
import com.aquilabank.domain.auth.port.RefreshTokenSessionWritePort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class ExternalOidcLoginServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-21T00:00:00Z");
  private static final AuthSessionClientMetadata SESSION_CLIENT_METADATA =
      new AuthSessionClientMetadata("macOS / Safari", "203.0.113.20");

  @Test
  void issuesInternalTokenPairForLinkedActiveIdentity() {
    ExternalIdentityUserLoadPort externalIdentityUserLoadPort =
        Mockito.mock(ExternalIdentityUserLoadPort.class);
    LoginAttemptUpdatePort loginAttemptUpdatePort = Mockito.mock(LoginAttemptUpdatePort.class);
    LoginAttemptAuditPort loginAttemptAuditPort = Mockito.mock(LoginAttemptAuditPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        Mockito.mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = Mockito.mock(RefreshTokenSecretPort.class);
    RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort =
        Mockito.mock(RefreshDeviceBindingSecretPort.class);
    AuthTokenIssuePort authTokenIssuePort = Mockito.mock(AuthTokenIssuePort.class);

    when(externalIdentityUserLoadPort.findByProviderIdAndSubjectForUpdate(
            "google", "oidc-subject-1"))
        .thenReturn(
            Optional.of(
                new LoginUser(
                    7L, "alice", "hash", UserStatus.ACTIVE, 2, NOW.minusSeconds(60), null, null)));
    when(refreshTokenSecretPort.createToken()).thenReturn("refresh-token");
    when(refreshTokenSecretPort.hash("refresh-token")).thenReturn("refresh-hash");
    when(refreshDeviceBindingSecretPort.createToken()).thenReturn("binding-token");
    when(refreshDeviceBindingSecretPort.hash("binding-token")).thenReturn("binding-hash");
    when(refreshTokenSessionWritePort.create(
            argThat(
                command ->
                    command.userId() == 7L
                        && "refresh-hash".equals(command.tokenHash())
                        && "binding-hash".equals(command.deviceBindingHash())
                        && NOW.plus(Duration.ofDays(14)).equals(command.expiresAt())
                        && SESSION_CLIENT_METADATA.equals(command.sessionClientMetadata()))))
        .thenReturn(31L);
    when(authTokenIssuePort.issue(7L, "alice", 31L, NOW))
        .thenReturn(new IssuedAccessToken("access-token", "Bearer", NOW.plusSeconds(900), 7L));

    ExternalOidcLoginService service =
        new ExternalOidcLoginService(
            externalIdentityUserLoadPort,
            loginAttemptUpdatePort,
            loginAttemptAuditPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            authTokenIssuePort,
            new RefreshTokenPolicy(Duration.ofDays(14)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    LoginResult result =
        service.login(
            new ExternalOidcLoginCommand("google", "oidc-subject-1", SESSION_CLIENT_METADATA));

    assertThat(result.status()).isEqualTo(LoginResultStatus.SUCCESS);
    assertThat(result.accessToken()).isEqualTo("access-token");
    assertThat(result.refreshToken()).isEqualTo("refresh-token");
    assertThat(result.refreshDeviceBindingToken()).isEqualTo("binding-token");
    assertThat(result.userId()).isEqualTo(7L);
    verify(loginAttemptUpdatePort)
        .recordLoginSuccess(
            argThat(command -> command.userId() == 7L && NOW.equals(command.succeededAt())));
    verify(loginAttemptAuditPort)
        .logReset(
            argThat(
                entry ->
                    "alice".equals(entry.loginId())
                        && entry.userId() == 7L
                        && entry.previousFailureCount() == 2));
  }

  @Test
  void rejectsUnlinkedIdentityWithoutIssuingTokens() {
    ExternalIdentityUserLoadPort externalIdentityUserLoadPort =
        Mockito.mock(ExternalIdentityUserLoadPort.class);
    LoginAttemptUpdatePort loginAttemptUpdatePort = Mockito.mock(LoginAttemptUpdatePort.class);
    LoginAttemptAuditPort loginAttemptAuditPort = Mockito.mock(LoginAttemptAuditPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        Mockito.mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = Mockito.mock(RefreshTokenSecretPort.class);
    RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort =
        Mockito.mock(RefreshDeviceBindingSecretPort.class);
    AuthTokenIssuePort authTokenIssuePort = Mockito.mock(AuthTokenIssuePort.class);

    when(externalIdentityUserLoadPort.findByProviderIdAndSubjectForUpdate(
            "google", "unknown-subject"))
        .thenReturn(Optional.empty());

    ExternalOidcLoginService service =
        new ExternalOidcLoginService(
            externalIdentityUserLoadPort,
            loginAttemptUpdatePort,
            loginAttemptAuditPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            authTokenIssuePort,
            new RefreshTokenPolicy(Duration.ofDays(14)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThrows(
        InvalidCredentialsException.class,
        () ->
            service.login(
                new ExternalOidcLoginCommand(
                    "google", "unknown-subject", SESSION_CLIENT_METADATA)));

    verify(externalIdentityUserLoadPort)
        .findByProviderIdAndSubjectForUpdate("google", "unknown-subject");
    verifyNoInteractions(
        loginAttemptUpdatePort,
        loginAttemptAuditPort,
        refreshTokenSessionWritePort,
        refreshTokenSecretPort,
        refreshDeviceBindingSecretPort,
        authTokenIssuePort);
  }

  @Test
  void rejectsInactiveOrTemporarilyLockedUser() {
    ExternalIdentityUserLoadPort externalIdentityUserLoadPort =
        Mockito.mock(ExternalIdentityUserLoadPort.class);
    LoginAttemptUpdatePort loginAttemptUpdatePort = Mockito.mock(LoginAttemptUpdatePort.class);
    LoginAttemptAuditPort loginAttemptAuditPort = Mockito.mock(LoginAttemptAuditPort.class);
    RefreshTokenSessionWritePort refreshTokenSessionWritePort =
        Mockito.mock(RefreshTokenSessionWritePort.class);
    RefreshTokenSecretPort refreshTokenSecretPort = Mockito.mock(RefreshTokenSecretPort.class);
    RefreshDeviceBindingSecretPort refreshDeviceBindingSecretPort =
        Mockito.mock(RefreshDeviceBindingSecretPort.class);
    AuthTokenIssuePort authTokenIssuePort = Mockito.mock(AuthTokenIssuePort.class);

    when(externalIdentityUserLoadPort.findByProviderIdAndSubjectForUpdate(
            "google", "locked-subject"))
        .thenReturn(
            Optional.of(
                new LoginUser(
                    8L,
                    "locked-user",
                    "hash",
                    UserStatus.ACTIVE,
                    5,
                    NOW.minusSeconds(30),
                    NOW.plusSeconds(300),
                    null)));

    ExternalOidcLoginService service =
        new ExternalOidcLoginService(
            externalIdentityUserLoadPort,
            loginAttemptUpdatePort,
            loginAttemptAuditPort,
            refreshTokenSessionWritePort,
            refreshTokenSecretPort,
            refreshDeviceBindingSecretPort,
            authTokenIssuePort,
            new RefreshTokenPolicy(Duration.ofDays(14)),
            Clock.fixed(NOW, ZoneOffset.UTC));

    assertThrows(
        InvalidCredentialsException.class,
        () ->
            service.login(
                new ExternalOidcLoginCommand("google", "locked-subject", SESSION_CLIENT_METADATA)));

    verify(loginAttemptUpdatePort, never()).recordLoginSuccess(Mockito.any());
    verifyNoInteractions(refreshTokenSessionWritePort, refreshTokenSecretPort, authTokenIssuePort);
  }
}
