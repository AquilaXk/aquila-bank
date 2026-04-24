package com.aquilabank.global.auth;

import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryDeliveryResult;
import com.aquilabank.domain.auth.port.PasswordRecoveryDeliveryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 로컬/운영 점검용 metadata만 기록하고 recovery token 원문은 남기지 않습니다. */
public final class LoggingPasswordRecoveryDeliveryAdapter implements PasswordRecoveryDeliveryPort {

  private static final Logger log =
      LoggerFactory.getLogger(LoggingPasswordRecoveryDeliveryAdapter.class);

  @Override
  public PasswordRecoveryDeliveryResult deliver(PasswordRecoveryDeliveryCommand command) {
    log.info(
        "password recovery delivery requested requestId={} userId={} expiresAt={}",
        command.requestId(),
        command.userId(),
        command.expiresAt());
    return PasswordRecoveryDeliveryResult.delivered();
  }
}
