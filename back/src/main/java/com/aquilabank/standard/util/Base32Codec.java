package com.aquilabank.standard.util;

/** TOTP manual entry key 인코딩/디코딩에 필요한 Base32만 최소 구현합니다. */
public final class Base32Codec {

  private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
  private static final int[] LOOKUP = new int[128];

  static {
    java.util.Arrays.fill(LOOKUP, -1);
    for (int index = 0; index < ALPHABET.length; index++) {
      char upper = ALPHABET[index];
      LOOKUP[upper] = index;
      if (upper >= 'A' && upper <= 'Z') {
        LOOKUP[upper - 'A' + 'a'] = index;
      }
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
    if (value == null) {
      throw new IllegalArgumentException("value is required");
    }
    int validCharCount = 0;
    for (int index = 0; index < value.length(); index++) {
      char current = value.charAt(index);
      if (isIgnorableSeparator(current)) {
        continue;
      }
      if (current >= LOOKUP.length || LOOKUP[current] < 0) {
        throw new IllegalArgumentException("invalid Base32 character");
      }
      validCharCount++;
    }
    if (validCharCount == 0) {
      throw new IllegalArgumentException("value is required");
    }

    // 유효 문자 수로 출력 바이트 배열 크기를 한 번에 계산해 중간 스트림/문자열 할당 없이 비트 패킹합니다.
    byte[] output = new byte[(validCharCount * 5) >>> 3];
    int outIndex = 0;
    int buffer = 0;
    int bitsLeft = 0;
    for (int index = 0; index < value.length(); index++) {
      char current = value.charAt(index);
      if (isIgnorableSeparator(current)) {
        continue;
      }
      buffer = (buffer << 5) | LOOKUP[current];
      bitsLeft += 5;
      if (bitsLeft >= 8) {
        output[outIndex++] = (byte) ((buffer >> (bitsLeft - 8)) & 0xff);
        bitsLeft -= 8;
      }
    }
    return output;
  }

  private static boolean isIgnorableSeparator(char current) {
    return current == '-' || Character.isWhitespace(current);
  }
}
