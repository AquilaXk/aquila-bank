package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.LoginCommand;
import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.model.LoginUser;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.port.AuthTokenIssuePort;
import com.aquilabank.domain.auth.port.PasswordHashPort;
import com.aquilabank.domain.auth.port.UserCredentialLoadPort;

/** loginId/password 검증 후 JWT 발급 port로 위임합니다. */
public final class LoginService implements LoginUseCase {

  private final UserCredentialLoadPort userCredentialLoadPort;
  private final PasswordHashPort passwordHashPort;
  private final AuthTokenIssuePort authTokenIssuePort;

  public LoginService(
      UserCredentialLoadPort userCredentialLoadPort,
      PasswordHashPort passwordHashPort,
      AuthTokenIssuePort authTokenIssuePort) {
    this.userCredentialLoadPort = userCredentialLoadPort;
    this.passwordHashPort = passwordHashPort;
    this.authTokenIssuePort = authTokenIssuePort;
  }

  @Override
  public LoginResult login(LoginCommand command) {
    LoginUser user =
        userCredentialLoadPort
            .findByLoginId(command.loginId())
            .orElseThrow(() -> new InvalidCredentialsException("login failed"));

    if (user.status() != UserStatus.ACTIVE
        || !passwordHashPort.matches(command.password(), user.passwordHash())) {
      throw new InvalidCredentialsException("login failed");
    }

    return authTokenIssuePort.issue(user.userId(), user.loginId());
  }
}
