package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.BackupCodeRecord;
import java.util.Optional;

/** backup code exact lookup은 user 범위와 hash를 같이 고정해 작은 row만 읽습니다. */
public interface BackupCodeLoadPort {

  Optional<BackupCodeRecord> findActiveByUserIdAndCodeHashForUpdate(long userId, String codeHash);
}
