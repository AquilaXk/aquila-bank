package com.aquilabank.global.notification;

import java.util.List;

/** user stream fan-out 대상은 active membership/exact query 결과만 사용합니다. */
public interface NotificationSseTargetResolver {

  List<Long> findActiveUserIdsByAccountId(long accountId);
}
