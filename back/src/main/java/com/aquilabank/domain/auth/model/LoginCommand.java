package com.aquilabank.domain.auth.model;

/** 로그인 자격 증명 입력 */
public record LoginCommand(
    String loginId,
    String password,
    AuthSessionClientMetadata sessionClientMetadata,
    String rememberDeviceToken) {

  public LoginCommand(
      String loginId, String password, AuthSessionClientMetadata sessionClientMetadata) {
    this(loginId, password, sessionClientMetadata, null);
  }

  public LoginCommand {
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("loginId is required");
    }
    if (password == null || password.isBlank()) {
      throw new IllegalArgumentException("password is required");
    }
    if (sessionClientMetadata == null) {
      throw new IllegalArgumentException("sessionClientMetadata is required");
    }
    if (rememberDeviceToken != null && rememberDeviceToken.isBlank()) {
      rememberDeviceToken = null;
    }
  }
}
