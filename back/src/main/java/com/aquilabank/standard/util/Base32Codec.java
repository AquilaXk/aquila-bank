package com.aquilabank.standard.util;

/** TOTP manual entry key 인코딩/디코딩에 필요한 Base32만 최소 구현합니다. */
public final class Base32Codec {

  private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
  private static final int[] LOOKUP = new int[128];

  static {
    java.util.Arrays.fill(LOOKUP, -1);
    for (int index = 0; index < ALPHABET.length; index++) {
      LOOKUP[ALPHABET[index]] = index;
    }
  }

  private Base32Codec() {}

  public static String encode(byte[] value) {
    if (value == null || value.length == 0) {
      throw new IllegalArgumentException("value is required");
    }
    StringBuilder builder = new StringBuilder((value.length * 8 + 4) / 5);
    int buffer = value[0] & 0xff;
    int next = 1;
    int bitsLeft = 8;
    while (bitsLeft > 0 || next < value.length) {
      if (bitsLeft < 5) {
        if (next < value.length) {
          buffer <<= 8;
          buffer |= value[next++] & 0xff;
          bitsLeft += 8;
        } else {
          int pad = 5 - bitsLeft;
          buffer <<= pad;
          bitsLeft += pad;
        }
      }
      int index = (buffer >> (bitsLeft - 5)) & 0x1f;
      bitsLeft -= 5;
      builder.append(ALPHABET[index]);
    }
    return builder.toString();
  }

  public static byte[] decode(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("value is required");
    }
    String normalized =
        value.replace(" ", "").replace("-", "").trim().toUpperCase(java.util.Locale.ROOT);
    if (normalized.isEmpty()) {
      throw new IllegalArgumentException("value is required");
    }
    java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
    int buffer = 0;
    int bitsLeft = 0;
    for (int index = 0; index < normalized.length(); index++) {
      char current = normalized.charAt(index);
      if (current >= LOOKUP.length || LOOKUP[current] < 0) {
        throw new IllegalArgumentException("invalid Base32 character");
      }
      buffer <<= 5;
      buffer |= LOOKUP[current];
      bitsLeft += 5;
      if (bitsLeft >= 8) {
        outputStream.write((buffer >> (bitsLeft - 8)) & 0xff);
        bitsLeft -= 8;
      }
    }
    return outputStream.toByteArray();
  }
}
