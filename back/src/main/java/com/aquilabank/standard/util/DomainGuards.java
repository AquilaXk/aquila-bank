package com.aquilabank.standard.util;

/** 도메인 입력과 불변식 경계를 공통 검증하는 무상태 가드 유틸리티입니다. */
public final class DomainGuards {

  private DomainGuards() {}

  public static long requirePositive(long value, String fieldName) {
    if (value <= 0) {
      throw new IllegalArgumentException(fieldName + " must be positive");
    }
    return value;
  }

  public static long requireNonNegative(long value, String fieldName) {
    if (value < 0) {
      throw new IllegalArgumentException(fieldName + " must be zero or positive");
    }
    return value;
  }

  public static String requireText(String value, int maxLength, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    String trimmed = value.trim();
    if (trimmed.length() > maxLength) {
      throw new IllegalArgumentException(
          fieldName + " must be " + maxLength + " characters or less");
    }
    return trimmed;
  }

  public static String requireOptionalText(String value, int maxLength, String fieldName) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    if (trimmed.length() > maxLength) {
      throw new IllegalArgumentException(
          fieldName + " must be " + maxLength + " characters or less");
    }
    return trimmed;
  }

  public static String requireCurrencyCode(String value) {
    if (value == null || !isThreeLetterUpperAscii(value)) {
      throw new IllegalArgumentException("currencyCode must be a 3-letter uppercase code");
    }
    return value;
  }

  private static boolean isThreeLetterUpperAscii(String value) {
    return value.length() == 3
        && isUpperAscii(value.charAt(0))
        && isUpperAscii(value.charAt(1))
        && isUpperAscii(value.charAt(2));
  }

  private static boolean isUpperAscii(char ch) {
    return ch >= 'A' && ch <= 'Z';
  }
}
