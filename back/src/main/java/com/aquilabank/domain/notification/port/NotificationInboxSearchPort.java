package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.NotificationSearchQuery;
import com.aquilabank.domain.notification.model.NotificationSearchSlice;

/** 검색 전용 inbox 조회는 기존 목록 API와 분리된 조건/window 계약을 사용합니다. */
public interface NotificationInboxSearchPort {

  NotificationSearchSlice searchByUserId(long userId, NotificationSearchQuery query);

  NotificationSearchSlice searchByAccountId(long accountId, NotificationSearchQuery query);
}
