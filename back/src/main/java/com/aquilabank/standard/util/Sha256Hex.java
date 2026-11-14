package com.aquilabank.standard.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

public final class Sha256Hex {

  private static final String SHA_256_ALGORITHM = "SHA-256";
  private static final char[] HEX_DIGITS = "0123456789abcdef".toCharArray();
  // 스레드별 MessageDigest와 64자 출력 버퍼를 재사용해 핫 경로의 객체 할당을 없앤다.
  private static final ThreadLocal<MessageDigest> SHA_256_DIGEST =
      ThreadLocal.withInitial(() -> messageDigest(SHA_256_ALGORITHM));
  private static final ThreadLocal<char[]> HEX_BUFFER = ThreadLocal.withInitial(() -> new char[64]);

  private Sha256Hex() {}

  public static String hashHex(String value) {
    Objects.requireNonNull(value, "value must not be null");
    return hashHex(value.getBytes(StandardCharsets.UTF_8));
  }

  public static String hashHex(byte[] bytes) {
    byte[] hash = digest(bytes);
    char[] hex = HEX_BUFFER.get();
    for (int i = 0; i < 32; i++) {
      int b = hash[i] & 0xFF;
      hex[i << 1] = HEX_DIGITS[b >>> 4];
      hex[(i << 1) | 1] = HEX_DIGITS[b & 0x0F];
    }
    return new String(hex);
  }

  public static byte[] digest(byte[] bytes) {
    Objects.requireNonNull(bytes, "bytes must not be null");
    MessageDigest messageDigest = SHA_256_DIGEST.get();
    messageDigest.reset();
    return messageDigest.digest(bytes);
  }

  public static MessageDigest messageDigest(String algorithm) {
    try {
      return MessageDigest.getInstance(algorithm);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(algorithm + " is not available", exception);
    }
  }
}
