package com.aquilabank.global.auth;

import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryCommand;
import com.aquilabank.domain.auth.port.PasswordRecoveryDeliveryPort;

/** 외부 전달 채널이 없을 때 token을 노출하지 않는 기본 adapter입니다. */
public final class NoOpPasswordRecoveryDeliveryAdapter implements PasswordRecoveryDeliveryPort {

  @Override
  public void deliver(PasswordRecoveryDeliveryCommand command) {
    // 기본 경로는 외부 provider 없이 token 저장/confirm 계약만 유지합니다.
  }
}
