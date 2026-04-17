package com.aquilabank.domain.account.model;

import java.time.Instant;

/** 고객 계좌 단건 조회에 필요한 최소 요약 모델 */
public record AccountSummary(
    long accountId,
    String accountNumber,
    String displayName,
    String accountStatus,
    String currencyCode,
    long availableBalanceMinor,
    long pendingBalanceMinor,
    Instant createdAt,
    Instant balanceUpdatedAt) {

  public AccountSummary {
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    if (accountNumber == null || accountNumber.isBlank() || accountNumber.length() > 20) {
      throw new IllegalArgumentException("accountNumber must be between 1 and 20 characters");
    }
    if (displayName == null || displayName.isBlank()) {
      throw new IllegalArgumentException("displayName is required");
    }
    if (accountStatus == null || accountStatus.isBlank()) {
      throw new IllegalArgumentException("accountStatus is required");
    }
    if (currencyCode == null || !currencyCode.matches("^[A-Z]{3}$")) {
      throw new IllegalArgumentException("currencyCode must be a 3-letter uppercase code");
    }
    if (availableBalanceMinor < 0) {
      throw new IllegalArgumentException("availableBalanceMinor must be zero or positive");
    }
    if (pendingBalanceMinor < 0) {
      throw new IllegalArgumentException("pendingBalanceMinor must be zero or positive");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt must not be null");
    }
    if (balanceUpdatedAt == null) {
      throw new IllegalArgumentException("balanceUpdatedAt must not be null");
    }
  }
}
