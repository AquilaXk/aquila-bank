package com.aquilabank.domain.account.model;

import java.util.Locale;

/** 계좌 원본 row와 opening balance를 함께 초기화하는 bootstrap 명령 */
public record AccountBootstrapCommand(
    String displayName, String currencyCode, long initialBalanceMinor) {

  public AccountBootstrapCommand {
    if (displayName == null || displayName.isBlank()) {
      throw new IllegalArgumentException("displayName must not be blank");
    }
    displayName = displayName.trim();
    if (displayName.length() > 80) {
      throw new IllegalArgumentException("displayName must be 80 characters or less");
    }

    if (currencyCode == null || currencyCode.isBlank()) {
      throw new IllegalArgumentException("currencyCode must not be blank");
    }
    currencyCode = currencyCode.trim().toUpperCase(Locale.ROOT);
    if (!isThreeLetterUpperAscii(currencyCode)) {
      throw new IllegalArgumentException("currencyCode must be a 3-letter uppercase code");
    }

    if (initialBalanceMinor < 0) {
      throw new IllegalArgumentException("initialBalanceMinor must be zero or positive");
    }
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
