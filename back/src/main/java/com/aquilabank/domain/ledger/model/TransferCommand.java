package com.aquilabank.domain.ledger.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 송금 명령 입력 모델 */
public record TransferCommand(
    long sourceAccountId,
    long targetAccountId,
    long amountMinor,
    String currencyCode,
    String summary,
    String idempotencyKey) {

  public TransferCommand {
    if (sourceAccountId <= 0) {
      throw new IllegalArgumentException("sourceAccountId must be positive");
    }
    if (targetAccountId <= 0) {
      throw new IllegalArgumentException("targetAccountId must be positive");
    }
    if (sourceAccountId == targetAccountId) {
      throw new IllegalArgumentException("sourceAccountId and targetAccountId must differ");
    }
    if (amountMinor <= 0) {
      throw new IllegalArgumentException("amountMinor must be positive");
    }
    if (currencyCode == null || !isThreeLetterUpperAscii(currencyCode)) {
      throw new IllegalArgumentException("currencyCode must be a 3-letter uppercase code");
    }
    if (summary == null || summary.isBlank() || summary.length() > 120) {
      throw new IllegalArgumentException("summary must be between 1 and 120 characters");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 80) {
      throw new IllegalArgumentException("idempotencyKey must be between 1 and 80 characters");
    }
  }

  public String fingerprint() {
    // summary 길이 제한(120자)과 구분자('|') 충돌에 영향받지 않도록 길이 접두어 정규화 후 SHA-256 hex(64자)로 고정합니다.
    String canonical =
        "TRANSFER|"
            + sourceAccountId
            + "|"
            + targetAccountId
            + "|"
            + amountMinor
            + "|"
            + currencyCode
            + "|"
            + summary.length()
            + ":"
            + summary;
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 is not available", ex);
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
