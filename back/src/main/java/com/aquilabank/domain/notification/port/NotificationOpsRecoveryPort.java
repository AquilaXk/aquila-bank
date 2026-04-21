package com.aquilabank.domain.notification.port;

import com.aquilabank.domain.notification.model.NotificationDlqRedriveCommand;
import com.aquilabank.domain.notification.model.NotificationDlqRedriveResult;

/** notification DLQ redrive 는 Kafka adapter 가 source record 조회와 재발행을 맡습니다. */
public interface NotificationOpsRecoveryPort {

  NotificationDlqRedriveResult redrive(NotificationDlqRedriveCommand command);
}
