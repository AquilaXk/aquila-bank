package com.aquilabank.domain.auth.model;

/** password recovery provider 결과를 실제 발송 성공과 fail-safe skip으로 분리합니다. */
public record PasswordRecoveryDeliveryResult(
    boolean success, PasswordRecoveryDeliverySkipReason skipReason) {

  public PasswordRecoveryDeliveryResult {
    if (success && skipReason != null) {
      throw new IllegalArgumentException("sent result must not have skipReason");
    }
    if (!success && skipReason == null) {
      throw new IllegalArgumentException("skipped result requires skipReason");
    }
  }

  public boolean sent() {
    return success;
  }

  public static PasswordRecoveryDeliveryResult delivered() {
    return new PasswordRecoveryDeliveryResult(true, null);
  }

  public static PasswordRecoveryDeliveryResult skipped(
      PasswordRecoveryDeliverySkipReason skipReason) {
    return new PasswordRecoveryDeliveryResult(false, skipReason);
  }
}
