package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.LoginFailureUpdateCommand;
import com.aquilabank.domain.auth.model.LoginSuccessUpdateCommand;

/** login 실패 누적과 성공 reset 저장을 write port로 분리합니다. */
public interface LoginAttemptUpdatePort {

  void recordLoginFailure(LoginFailureUpdateCommand command);

  void recordLoginSuccess(LoginSuccessUpdateCommand command);
}
