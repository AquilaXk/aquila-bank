package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.LoginFailureAuditEntry;
import com.aquilabank.domain.auth.model.LoginResetAuditEntry;

/** login 실패/해제 운영 흔적을 logging adapter로 넘기는 port 입니다. */
public interface LoginAttemptAuditPort {

  void logFailure(LoginFailureAuditEntry entry);

  void logReset(LoginResetAuditEntry entry);
}
