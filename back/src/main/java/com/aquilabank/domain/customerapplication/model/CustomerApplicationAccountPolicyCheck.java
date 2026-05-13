package com.aquilabank.domain.customerapplication.model;

/** 신청 실행 직전 계좌/권한 상태를 다시 확인한 결과입니다. */
public record CustomerApplicationAccountPolicyCheck(boolean permitted, String rejectionReason) {

  public CustomerApplicationAccountPolicyCheck {
    if (permitted) {
      rejectionReason = null;
    } else if (rejectionReason == null || rejectionReason.isBlank()) {
      throw new IllegalArgumentException("rejectionReason is required when rejected");
    }
  }

  public static CustomerApplicationAccountPolicyCheck allowed() {
    return new CustomerApplicationAccountPolicyCheck(true, null);
  }

  public static CustomerApplicationAccountPolicyCheck rejected(String rejectionReason) {
    return new CustomerApplicationAccountPolicyCheck(false, rejectionReason);
  }
}
