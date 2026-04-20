package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.PasswordRecoveryTokenRecord;
import java.util.Optional;

/** tokenHash exact lookup을 위한 읽기 경로입니다. */
public interface PasswordRecoveryTokenLoadPort {

  Optional<PasswordRecoveryTokenRecord> findByTokenHashForUpdate(String tokenHash);
}
