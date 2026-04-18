package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 로그인 성공 후 반환하는 bearer token 결과 */
public record LoginResult(
    LoginResultStatus status,
    String accessToken,
    String refreshToken,
    String tokenType,
    Instant expiresAt,
    Instant refreshExpiresAt,
    Long userId,
    String challengeId,
    LoginChallengeType challengeType,
    Instant challengeExpiresAt) {

  public LoginResult {
    if (status == null) {
      throw new IllegalArgumentException("status is required");
    }
    if (status == LoginResultStatus.SUCCESS) {
      if (accessToken == null || accessToken.isBlank()) {
        throw new IllegalArgumentException("accessToken is required");
      }
      if (refreshToken == null || refreshToken.isBlank()) {
        throw new IllegalArgumentException("refreshToken is required");
      }
      if (tokenType == null || tokenType.isBlank()) {
        throw new IllegalArgumentException("tokenType is required");
      }
      if (expiresAt == null) {
        throw new IllegalArgumentException("expiresAt is required");
      }
      if (refreshExpiresAt == null) {
        throw new IllegalArgumentException("refreshExpiresAt is required");
      }
      if (userId == null || userId <= 0) {
        throw new IllegalArgumentException("userId must be positive");
      }
      if (challengeId != null || challengeType != null || challengeExpiresAt != null) {
        throw new IllegalArgumentException("challenge fields are not allowed for success");
      }
    } else {
      if (challengeId == null || challengeId.isBlank()) {
        throw new IllegalArgumentException("challengeId is required");
      }
      if (challengeType == null) {
        throw new IllegalArgumentException("challengeType is required");
      }
      if (challengeExpiresAt == null) {
        throw new IllegalArgumentException("challengeExpiresAt is required");
      }
      if (accessToken != null
          || refreshToken != null
          || tokenType != null
          || expiresAt != null
          || refreshExpiresAt != null
          || userId != null) {
        throw new IllegalArgumentException("token fields are not allowed for mfa challenge");
      }
    }
  }

  public static LoginResult success(
      String accessToken,
      String refreshToken,
      String tokenType,
      Instant expiresAt,
      Instant refreshExpiresAt,
      long userId) {
    return new LoginResult(
        LoginResultStatus.SUCCESS,
        accessToken,
        refreshToken,
        tokenType,
        expiresAt,
        refreshExpiresAt,
        userId,
        null,
        null,
        null);
  }

  public static LoginResult mfaRequired(
      String challengeId, LoginChallengeType challengeType, Instant challengeExpiresAt) {
    return new LoginResult(
        LoginResultStatus.MFA_REQUIRED,
        null,
        null,
        null,
        null,
        null,
        null,
        challengeId,
        challengeType,
        challengeExpiresAt);
  }
}
