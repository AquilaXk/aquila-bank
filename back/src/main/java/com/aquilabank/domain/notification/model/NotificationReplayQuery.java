package com.aquilabank.domain.notification.model;

/** SSE reconnect gap 복구용 replay 조건 */
public record NotificationReplayQuery(long lastEventId, int limit) {

  public static final int MAX_LIMIT = 100;

  public NotificationReplayQuery {
    if (lastEventId <= 0) {
      throw new IllegalArgumentException("lastEventId must be positive");
    }
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("limit must be between 1 and 100");
    }
  }
}
