package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryOutboxEntry;

/** password recovery request와 외부 provider delivery를 분리하는 auth outbox append 경로입니다. */
public interface PasswordRecoveryDeliveryOutboxAppendPort {

  void append(PasswordRecoveryDeliveryOutboxEntry entry);
}
