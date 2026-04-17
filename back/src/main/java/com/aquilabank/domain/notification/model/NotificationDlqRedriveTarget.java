package com.aquilabank.domain.notification.model;

/** DLQ preview 응답 좌표를 그대로 redrive 입력으로 재사용합니다. */
public record NotificationDlqRedriveTarget(int partition, long offset) {

  public NotificationDlqRedriveTarget {
    if (partition < 0) {
      throw new IllegalArgumentException("partition must not be negative");
    }
    if (offset < 0) {
      throw new IllegalArgumentException("offset must not be negative");
    }
  }
}
