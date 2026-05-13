package com.aquilabank.domain.customerapplication.model;

/** 승인 payload가 은행 risk cap을 우회하지 못하도록 신청/실행 양쪽에서 공유하는 한도 상한입니다. */
public record CustomerTransferLimitChangePolicy(
    long maxSingleTransferLimitMinor, long maxDailyTransferLimitMinor) {

  public CustomerTransferLimitChangePolicy {
    if (maxSingleTransferLimitMinor <= 0) {
      throw new IllegalArgumentException("maxSingleTransferLimitMinor must be positive");
    }
    if (maxDailyTransferLimitMinor <= 0) {
      throw new IllegalArgumentException("maxDailyTransferLimitMinor must be positive");
    }
    if (maxDailyTransferLimitMinor < maxSingleTransferLimitMinor) {
      throw new IllegalArgumentException(
          "maxDailyTransferLimitMinor must be greater than or equal to maxSingleTransferLimitMinor");
    }
  }

  public void validate(long singleTransferLimitMinor, long dailyTransferLimitMinor) {
    if (singleTransferLimitMinor <= 0 || dailyTransferLimitMinor <= 0) {
      throw new IllegalArgumentException("transfer limits must be positive");
    }
    if (dailyTransferLimitMinor < singleTransferLimitMinor) {
      throw new IllegalArgumentException(
          "dailyTransferLimitMinor must be greater than or equal to singleTransferLimitMinor");
    }
    if (singleTransferLimitMinor > maxSingleTransferLimitMinor
        || dailyTransferLimitMinor > maxDailyTransferLimitMinor) {
      throw new IllegalArgumentException("transfer limit change exceeds configured policy");
    }
  }

  public boolean allows(long singleTransferLimitMinor, long dailyTransferLimitMinor) {
    try {
      validate(singleTransferLimitMinor, dailyTransferLimitMinor);
      return true;
    } catch (IllegalArgumentException ex) {
      return false;
    }
  }
}
