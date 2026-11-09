package com.aquilabank.global.security;

import com.aquilabank.standard.util.Sha256Hex;
import java.security.SecureRandom;
import java.util.Base64;

/** opaque random 32-byte URL-safe 토큰 생성과 SHA-256 해시 계산을 공통 처리합니다. */
abstract class AbstractSha256TokenManager {

  private static final int TOKEN_BYTE_LENGTH = 32;

  private final SecureRandom secureRandom = new SecureRandom();

  public String createToken() {
    return generateOpaqueToken(TOKEN_BYTE_LENGTH);
  }

  protected String generateOpaqueToken(int byteLength) {
    if (byteLength <= 0) {
      throw new IllegalArgumentException("byteLength must be positive");
    }
    byte[] bytes = new byte[byteLength];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public String hash(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new IllegalArgumentException("rawToken is required");
    }
    return Sha256Hex.hashHex(rawToken);
  }
}
