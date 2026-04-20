package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.BackupCodeChallengeVerifyCommand;
import com.aquilabank.domain.auth.model.LoginResult;

/** backup code로 MFA challenge를 검증해 최종 token pair를 발급하는 진입점입니다. */
public interface BackupCodeChallengeVerifyUseCase {

  LoginResult verify(BackupCodeChallengeVerifyCommand command);
}
