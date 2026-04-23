package com.aquilabank.domain.notification.model;

/** projection drift 보정 결과를 scheduler 로그와 검증 기준에 같은 형태로 남깁니다. */
public record NotificationUnreadProjectionReconcileResult(int updatedCount, int zeroedCount) {

  public NotificationUnreadProjectionReconcileResult {
    if (updatedCount < 0) {
      throw new IllegalArgumentException("updatedCount must not be negative");
    }
    if (zeroedCount < 0) {
      throw new IllegalArgumentException("zeroedCount must not be negative");
    }
  }

  public int changedCount() {
    return updatedCount + zeroedCount;
  }
}
