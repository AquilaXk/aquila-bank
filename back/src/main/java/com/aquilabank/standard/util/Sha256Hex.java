package com.aquilabank.standard.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

public final class Sha256Hex {

  private static final String SHA_256_ALGORITHM = "SHA-256";
  private static final HexFormat HEX_FORMAT = HexFormat.of();
  // 스레드별 MessageDigest 인스턴스를 재사용해 핫 경로의 JCA 프로바이더 조회 비용을 제거한다.
  private static final ThreadLocal<MessageDigest> SHA_256_DIGEST =
      ThreadLocal.withInitial(() -> messageDigest(SHA_256_ALGORITHM));

  private Sha256Hex() {}

  public static String hashHex(String value) {
    Objects.requireNonNull(value, "value must not be null");
    return hashHex(value.getBytes(StandardCharsets.UTF_8));
  }

  public static String hashHex(byte[] bytes) {
    return HEX_FORMAT.formatHex(digest(bytes));
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
