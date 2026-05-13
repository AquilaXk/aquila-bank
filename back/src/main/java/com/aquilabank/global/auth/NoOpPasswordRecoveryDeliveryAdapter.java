package com.aquilabank.global.auth;

import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryResult;
import com.aquilabank.domain.auth.model.PasswordRecoveryDeliverySkipReason;
import com.aquilabank.domain.auth.port.PasswordRecoveryDeliveryPort;

/** 외부 전달 채널이 없을 때 token을 노출하지 않는 기본 adapter입니다. */
public final class NoOpPasswordRecoveryDeliveryAdapter implements PasswordRecoveryDeliveryPort {

  @Override
  public PasswordRecoveryDeliveryResult deliver(PasswordRecoveryDeliveryCommand command) {
    // provider 없는 기본 경로는 발송 성공으로 기록하지 않고 skip metric만 남깁니다.
    return PasswordRecoveryDeliveryResult.skipped(
        PasswordRecoveryDeliverySkipReason.PROVIDER_DISABLED);
  }
}
