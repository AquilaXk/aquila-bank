package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryCommand;

/** password recovery token 전달 책임을 domain 밖 adapter로 분리합니다. */
public interface PasswordRecoveryDeliveryPort {

  void deliver(PasswordRecoveryDeliveryCommand command);
}
