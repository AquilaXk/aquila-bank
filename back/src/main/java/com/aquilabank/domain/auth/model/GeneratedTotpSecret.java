package com.aquilabank.domain.auth.model;

/** TOTP 등록 응답과 보호 저장을 같이 만들기 위한 secret 생성 결과입니다. */
public record GeneratedTotpSecret(
    String secretKey, String otpauthUri, String secretCiphertext, String secretNonce) {

  public GeneratedTotpSecret {
    if (secretKey == null || secretKey.isBlank()) {
      throw new IllegalArgumentException("secretKey is required");
    }
    if (otpauthUri == null || otpauthUri.isBlank()) {
      throw new IllegalArgumentException("otpauthUri is required");
    }
    if (secretCiphertext == null || secretCiphertext.isBlank()) {
      throw new IllegalArgumentException("secretCiphertext is required");
    }
    if (secretNonce == null || secretNonce.isBlank()) {
      throw new IllegalArgumentException("secretNonce is required");
    }
  }
}
