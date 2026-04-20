package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.BackupCodeGenerateCommand;
import com.aquilabank.domain.auth.model.BackupCodeIssueResult;

/** 현재 TOTP 재검증 뒤 backup code 묶음을 재발급하는 진입점입니다. */
public interface BackupCodeGenerateUseCase {

  BackupCodeIssueResult issue(BackupCodeGenerateCommand command);
}
