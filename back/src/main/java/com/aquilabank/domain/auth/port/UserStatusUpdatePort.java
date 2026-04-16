package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.AuthUserSummary;
import com.aquilabank.domain.auth.model.UserStatusUpdateCommand;

/** 내부 auth 관리 경로의 user status update를 저장소 계층으로 위임합니다. */
public interface UserStatusUpdatePort {

  AuthUserSummary updateStatus(UserStatusUpdateCommand command);
}
