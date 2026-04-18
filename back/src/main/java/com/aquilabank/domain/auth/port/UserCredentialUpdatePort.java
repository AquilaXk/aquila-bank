package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.PasswordResetWriteCommand;

/** user credential 변경 write를 별도 port로 분리합니다. */
public interface UserCredentialUpdatePort {

  void resetPassword(PasswordResetWriteCommand command);
}
