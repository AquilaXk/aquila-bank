package com.aquilabank.global.notification;

/** SSE broker 보호 상한을 넘으면 새 구독을 즉시 거절합니다. */
public final class NotificationSseOverloadException extends RuntimeException {

  public NotificationSseOverloadException(String message) {
    super(message);
  }
}
