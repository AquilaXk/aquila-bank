package com.aquilabank.domain.notification.model;

/** provider 호출 결과를 실제 발송 성공과 fail-safe skip으로 분리합니다. */
public record NotificationChannelDeliveryResult(
    boolean success, NotificationChannelDeliverySkipReason skipReason) {

  public NotificationChannelDeliveryResult {
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

  public static NotificationChannelDeliveryResult delivered() {
    return new NotificationChannelDeliveryResult(true, null);
  }

  public static NotificationChannelDeliveryResult skipped(
      NotificationChannelDeliverySkipReason skipReason) {
    return new NotificationChannelDeliveryResult(false, skipReason);
  }
}
