package com.aquilabank.domain.ledger.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 송금 reversal 명령 입력 모델 */
public record TransferReversalCommand(
    String originalTransactionReference,
    long sourceAccountId,
    Long amountMinor,
    TransferReversalReason reversalReason,
    String summary,
    String idempotencyKey) {

  public TransferReversalCommand {
    if (originalTransactionReference == null
        || originalTransactionReference.isBlank()
        || originalTransactionReference.length() > 64) {
      throw new IllegalArgumentException(
          "originalTransactionReference must be between 1 and 64 characters");
    }
    if (sourceAccountId <= 0) {
      throw new IllegalArgumentException("sourceAccountId must be positive");
    }
    if (amountMinor != null && amountMinor <= 0) {
      throw new IllegalArgumentException("amountMinor must be positive");
    }
    if (reversalReason == null) {
      throw new IllegalArgumentException("reversalReason is required");
    }
    if (summary == null || summary.isBlank() || summary.length() > 120) {
      throw new IllegalArgumentException("summary must be between 1 and 120 characters");
    }
    if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 80) {
      throw new IllegalArgumentException("idempotencyKey must be between 1 and 80 characters");
    }
  }

  public String fingerprint() {
    // 가변 문자열 길이와 구분자 충돌에 무관하게 64자 SHA-256 hex로 request_fingerprint(VARCHAR(160))를 고정합니다.
    String canonical =
        "REVERSAL|"
            + originalTransactionReference.length()
            + ":"
            + originalTransactionReference
            + "|"
            + sourceAccountId
            + "|"
            + amountMinor
            + "|"
            + reversalReason
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
}
