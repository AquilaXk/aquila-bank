package com.aquilabank.domain.notification.exception;

/** 알림 read 처리 대상이 없거나 접근 범위 밖일 때 not found 로 숨깁니다. */
public class NotificationNotFoundException extends RuntimeException {

  public NotificationNotFoundException(String message) {
    super(message);
  }
}
