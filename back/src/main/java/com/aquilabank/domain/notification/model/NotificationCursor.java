package com.aquilabank.domain.notification.model;

import java.time.Instant;

/** inbox read model 정렬 컬럼으로 만드는 keyset cursor */
public record NotificationCursor(Instant createdAt, long id) {

  public NotificationCursor {
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt must not be null");
    }
    if (id <= 0) {
      throw new IllegalArgumentException("id must be positive");
    }
  }
}
