package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.BackupCodeIssueCommand;
import com.aquilabank.domain.auth.model.BackupCodeUseCommand;
import java.time.Instant;

/** backup code 발급, 재발급 supersede, 1회 사용 전이를 write port로 분리합니다. */
public interface BackupCodeWritePort {

  void supersedeActiveByUserId(long userId, Instant supersededAt);

  void issue(BackupCodeIssueCommand command);

  void markUsed(BackupCodeUseCommand command);
}
