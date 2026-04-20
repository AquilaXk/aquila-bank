package com.aquilabank.domain.auth.model;

/** 현재 JWT user가 종료하려는 refresh token session 최소 입력값입니다. */
public record LogoutCommand(long userId, String refreshToken, String rememberDeviceToken) {

  public LogoutCommand(long userId, String refreshToken) {
    this(userId, refreshToken, null);
  }

  public LogoutCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new IllegalArgumentException("refreshToken is required");
    }
    if (rememberDeviceToken != null && rememberDeviceToken.isBlank()) {
      rememberDeviceToken = null;
    }
  }
}
