package com.aquilabank.global.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

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
    MessageDigest digest = messageDigest("SHA-256");
    return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
  }

  static MessageDigest messageDigest(String algorithm) {
    try {
      return MessageDigest.getInstance(algorithm);
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(algorithm + " is not available", ex);
    }
  }
}
