package com.aquilabank.domain.auth.model;

/** recovery token 생성과 보호 저장값을 함께 담는 결과입니다. */
public record GeneratedPasswordRecoveryToken(
    String plainToken, String tokenHash, String tokenCiphertext, String tokenNonce) {

  public GeneratedPasswordRecoveryToken {
    if (plainToken == null || plainToken.isBlank()) {
      throw new IllegalArgumentException("plainToken is required");
    }
    if (tokenHash == null || tokenHash.isBlank()) {
      throw new IllegalArgumentException("tokenHash is required");
    }
    if (tokenCiphertext == null || tokenCiphertext.isBlank()) {
      throw new IllegalArgumentException("tokenCiphertext is required");
    }
    if (tokenNonce == null || tokenNonce.isBlank()) {
      throw new IllegalArgumentException("tokenNonce is required");
    }
  }
}
