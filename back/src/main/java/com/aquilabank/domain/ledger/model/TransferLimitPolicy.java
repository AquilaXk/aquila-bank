package com.aquilabank.domain.ledger.model;

public record TransferLimitPolicy(long singleTransferLimitMinor, long dailyTransferLimitMinor) {

  public TransferLimitPolicy {
    if (singleTransferLimitMinor <= 0) {
      throw new IllegalArgumentException("singleTransferLimitMinor must be positive");
    }
    if (dailyTransferLimitMinor <= 0) {
      throw new IllegalArgumentException("dailyTransferLimitMinor must be positive");
    }
    if (dailyTransferLimitMinor < singleTransferLimitMinor) {
      throw new IllegalArgumentException(
          "dailyTransferLimitMinor must be greater than or equal to singleTransferLimitMinor");
    }
  }
}
