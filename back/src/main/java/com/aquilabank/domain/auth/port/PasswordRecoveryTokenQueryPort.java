package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.PasswordRecoveryTokenQueryRecord;
import java.util.Optional;

/** requestId exact lookup을 위한 조회 경로입니다. */
public interface PasswordRecoveryTokenQueryPort {

  Optional<PasswordRecoveryTokenQueryRecord> findByRequestId(String requestId);
}
