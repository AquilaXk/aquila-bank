package com.aquilabank.global.notification;

/** 같은 인스턴스가 보낸 fan-out signal 재수신을 무시하려고 런타임별 식별자를 둡니다. */
public record NotificationSseFanoutInstanceId(String value) {

  public NotificationSseFanoutInstanceId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("value must not be blank");
    }
  }
}
