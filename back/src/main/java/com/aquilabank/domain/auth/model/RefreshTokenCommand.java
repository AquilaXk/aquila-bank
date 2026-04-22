package com.aquilabank.domain.auth.model;

/** refresh token 재발급 요청의 최소 입력값입니다. */
public record RefreshTokenCommand(
    String refreshToken,
    String refreshDeviceBindingToken,
    AuthSessionClientMetadata sessionClientMetadata,
    String requestId) {

  public RefreshTokenCommand(String refreshToken, AuthSessionClientMetadata sessionClientMetadata) {
    this(refreshToken, null, sessionClientMetadata, "-");
  }

  public RefreshTokenCommand(
      String refreshToken,
      String refreshDeviceBindingToken,
      AuthSessionClientMetadata sessionClientMetadata) {
    this(refreshToken, refreshDeviceBindingToken, sessionClientMetadata, "-");
  }

  public RefreshTokenCommand {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new IllegalArgumentException("refreshToken is required");
    }
    if (refreshDeviceBindingToken != null && refreshDeviceBindingToken.isBlank()) {
      refreshDeviceBindingToken = null;
    }
    if (sessionClientMetadata == null) {
      throw new IllegalArgumentException("sessionClientMetadata is required");
    }
    if (requestId == null || requestId.isBlank()) {
      requestId = "-";
    }
  }
}
