package com.aquilabank.domain.ledger.model;

import java.time.Instant;

/** 송금 reversal 완료 결과 모델 */
public record TransferReversalResult(
    String originalTransactionReference,
    String reversalTransactionReference,
    long sourceAccountId,
    long targetAccountId,
    long amountMinor,
    String currencyCode,
    long availableBalanceAfterMinor,
    Instant bookedAt,
    String status) {

  public TransferReversalResult {
    if (originalTransactionReference == null || originalTransactionReference.isBlank()) {
      throw new IllegalArgumentException("originalTransactionReference is required");
    }
    if (reversalTransactionReference == null || reversalTransactionReference.isBlank()) {
      throw new IllegalArgumentException("reversalTransactionReference is required");
    }
    if (sourceAccountId <= 0) {
      throw new IllegalArgumentException("sourceAccountId must be positive");
    }
    if (targetAccountId <= 0) {
      throw new IllegalArgumentException("targetAccountId must be positive");
    }
    if (amountMinor <= 0) {
      throw new IllegalArgumentException("amountMinor must be positive");
    }
    if (currencyCode == null || currencyCode.isBlank()) {
      throw new IllegalArgumentException("currencyCode is required");
    }
    if (bookedAt == null) {
      throw new IllegalArgumentException("bookedAt is required");
    }
    if (status == null || status.isBlank()) {
      throw new IllegalArgumentException("status is required");
    }
  }
}
