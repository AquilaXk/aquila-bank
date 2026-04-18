package com.aquilabank.domain.notification.port;

import java.time.Instant;
import java.util.List;

/** 읽음, archive, delete 같은 inbox 상태 전이를 persistence adapter 로 위임합니다. */
public interface NotificationInboxWritePort {

  boolean markAsReadByUserId(long userId, long notificationId, Instant readAt);

  boolean markAsReadByAccountId(long accountId, long notificationId, Instant readAt);

  int markAllAsReadByUserId(long userId, List<Long> notificationIds, Instant readAt);

  int markAllAsReadByAccountId(long accountId, List<Long> notificationIds, Instant readAt);

  int archiveByUserId(long userId, List<Long> notificationIds, Instant archivedAt);

  int archiveByAccountId(long accountId, List<Long> notificationIds, Instant archivedAt);

  int deleteByUserId(long userId, List<Long> notificationIds, Instant deletedAt);

  int deleteByAccountId(long accountId, List<Long> notificationIds);
}
