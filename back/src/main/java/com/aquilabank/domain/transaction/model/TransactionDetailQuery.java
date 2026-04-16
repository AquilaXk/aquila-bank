package com.aquilabank.domain.transaction.model;

/** source/target row 혼선을 막기 위한 account scope 포함 exact lookup 조건 */
public record TransactionDetailQuery(long accountId, String transactionReference) {

  public TransactionDetailQuery {
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    if (transactionReference == null || transactionReference.isBlank()) {
      throw new IllegalArgumentException("transactionReference must not be blank");
    }
    if (transactionReference.length() > 64) {
      throw new IllegalArgumentException("transactionReference must be 64 characters or less");
    }
  }
}
