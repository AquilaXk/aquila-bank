package com.aquilabank.domain.auth.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.model.AuthUserSummary;
import com.aquilabank.domain.auth.model.GeneratedPasswordRecoveryToken;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.PasswordRecoveryRequestCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryRequestResult;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenIssueCommand;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.PasswordRecoverySecretPort;
import com.aquilabank.domain.auth.port.PasswordRecoveryTokenWritePort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;
import com.aquilabank.domain.auth.port.UserQueryPort;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PasswordRecoveryRequestServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-17T01:00:00Z");
  private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
  private static final Duration TTL = Duration.ofMinutes(30);

  @Test
  void issuesTokenForActiveUserAndSupersedesPendingTokens() {
    UserQueryPort userQueryPort = mock(UserQueryPort.class);
    UserCredentialLoadPort userCredentialLoadPort = mock(UserCredentialLoadPort.class);
    PasswordRecoverySecretPort passwordRecoverySecretPort = mock(PasswordRecoverySecretPort.class);
    PasswordRecoveryTokenWritePort passwordRecoveryTokenWritePort =
        mock(PasswordRecoveryTokenWritePort.class);

    when(userQueryPort.findSummaryByLoginId("alice")).thenReturn(Optional.of(activeSummary()));
    when(userCredentialLoadPort.findByLoginIdForUpdate("alice"))
        .thenReturn(Optional.of(activeUser()));
    when(passwordRecoverySecretPort.generate())
        .thenReturn(
            new GeneratedPasswordRecoveryToken(
                "plain-token", "token-hash", "token-ciphertext", "token-nonce"));

    PasswordRecoveryRequestService service =
        new PasswordRecoveryRequestService(
            userQueryPort,
            userCredentialLoadPort,
            passwordRecoverySecretPort,
            passwordRecoveryTokenWritePort,
            TTL,
            CLOCK);

    PasswordRecoveryRequestResult result =
        service.request(new PasswordRecoveryRequestCommand("alice"));

    assertThat(result.handoffRequestId()).isNotBlank();
    verify(passwordRecoveryTokenWritePort).supersedePendingTokens(7L, NOW);

    ArgumentCaptor<PasswordRecoveryTokenIssueCommand> commandCaptor =
        ArgumentCaptor.forClass(PasswordRecoveryTokenIssueCommand.class);
    verify(passwordRecoveryTokenWritePort).issue(commandCaptor.capture());

    PasswordRecoveryTokenIssueCommand command = commandCaptor.getValue();
    assertThat(command.requestId()).isEqualTo(result.handoffRequestId());
    assertThat(command.userId()).isEqualTo(7L);
    assertThat(command.loginId()).isEqualTo("alice");
    assertThat(command.tokenHash()).isEqualTo("token-hash");
    assertThat(command.tokenCiphertext()).isEqualTo("token-ciphertext");
    assertThat(command.tokenNonce()).isEqualTo("token-nonce");
    assertThat(command.createdAt()).isEqualTo(NOW);
    assertThat(command.expiresAt()).isEqualTo(NOW.plus(TTL));
  }

  @Test
  void returnsHandoffRequestIdWithoutWritesWhenUserDoesNotExist() {
    UserQueryPort userQueryPort = mock(UserQueryPort.class);
    UserCredentialLoadPort userCredentialLoadPort = mock(UserCredentialLoadPort.class);
    PasswordRecoverySecretPort passwordRecoverySecretPort = mock(PasswordRecoverySecretPort.class);
    PasswordRecoveryTokenWritePort passwordRecoveryTokenWritePort =
        mock(PasswordRecoveryTokenWritePort.class);

    when(userQueryPort.findSummaryByLoginId("missing")).thenReturn(Optional.empty());

    PasswordRecoveryRequestService service =
        new PasswordRecoveryRequestService(
            userQueryPort,
            userCredentialLoadPort,
            passwordRecoverySecretPort,
            passwordRecoveryTokenWritePort,
            TTL,
            CLOCK);

    PasswordRecoveryRequestResult result =
        service.request(new PasswordRecoveryRequestCommand("missing"));

    assertThat(result.handoffRequestId()).isNotBlank();
    verify(userQueryPort).findSummaryByLoginId("missing");
    verifyNoInteractions(
        userCredentialLoadPort, passwordRecoverySecretPort, passwordRecoveryTokenWritePort);
  }

  @Test
  void returnsHandoffRequestIdWithoutWritesWhenUserIsInactive() {
    UserQueryPort userQueryPort = mock(UserQueryPort.class);
    UserCredentialLoadPort userCredentialLoadPort = mock(UserCredentialLoadPort.class);
    PasswordRecoverySecretPort passwordRecoverySecretPort = mock(PasswordRecoverySecretPort.class);
    PasswordRecoveryTokenWritePort passwordRecoveryTokenWritePort =
        mock(PasswordRecoveryTokenWritePort.class);

    when(userQueryPort.findSummaryByLoginId("alice")).thenReturn(Optional.of(inactiveSummary()));

    PasswordRecoveryRequestService service =
        new PasswordRecoveryRequestService(
            userQueryPort,
            userCredentialLoadPort,
            passwordRecoverySecretPort,
            passwordRecoveryTokenWritePort,
            TTL,
            CLOCK);

    PasswordRecoveryRequestResult result =
        service.request(new PasswordRecoveryRequestCommand("alice"));

    assertThat(result.handoffRequestId()).isNotBlank();
    verify(userQueryPort).findSummaryByLoginId("alice");
    verifyNoInteractions(
        userCredentialLoadPort, passwordRecoverySecretPort, passwordRecoveryTokenWritePort);
  }

  private AuthUserSummary activeSummary() {
    return new AuthUserSummary(
        7L,
        "alice",
        "Alice",
        UserStatus.ACTIVE,
        NOW.minus(Duration.ofDays(1)),
        NOW.minus(Duration.ofHours(1)));
  }

  private AuthUserSummary inactiveSummary() {
    return new AuthUserSummary(
        7L,
        "alice",
        "Alice",
        UserStatus.DISABLED,
        NOW.minus(Duration.ofDays(1)),
        NOW.minus(Duration.ofHours(1)));
  }

  private LoginUser activeUser() {
    return new LoginUser(7L, "alice", "stored-hash", UserStatus.ACTIVE, 0, null, null, NOW);
  }
}
