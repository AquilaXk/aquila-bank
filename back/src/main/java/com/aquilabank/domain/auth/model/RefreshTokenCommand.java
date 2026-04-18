package com.aquilabank.domain.auth.model;

/** refresh token 재발급 요청의 최소 입력값입니다. */
public record RefreshTokenCommand(
    String refreshToken, AuthSessionClientMetadata sessionClientMetadata) {

  public RefreshTokenCommand {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new IllegalArgumentException("refreshToken is required");
    }
    if (sessionClientMetadata == null) {
      throw new IllegalArgumentException("sessionClientMetadata is required");
    }
  }
}
