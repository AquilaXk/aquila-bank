package com.aquilabank.domain.account.model;

import java.time.Instant;

/** bootstrap 완료 후 후속 command/test가 재사용하는 계좌 식별 결과 */
public record AccountBootstrapResult(
    long accountId,
    String accountNumber,
    String displayName,
    String currencyCode,
    long availableBalanceMinor,
    String accountStatus,
    Instant createdAt) {

  public AccountBootstrapResult {
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    if (accountNumber == null || accountNumber.isBlank() || accountNumber.length() > 20) {
      throw new IllegalArgumentException("accountNumber must be between 1 and 20 characters");
    }
    if (displayName == null || displayName.isBlank()) {
      throw new IllegalArgumentException("displayName must not be blank");
    }
    if (currencyCode == null || !currencyCode.matches("^[A-Z]{3}$")) {
      throw new IllegalArgumentException("currencyCode must be a 3-letter uppercase code");
    }
    if (availableBalanceMinor < 0) {
      throw new IllegalArgumentException("availableBalanceMinor must be zero or positive");
    }
    if (accountStatus == null || accountStatus.isBlank()) {
      throw new IllegalArgumentException("accountStatus must not be blank");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt must not be null");
    }
  }
}
