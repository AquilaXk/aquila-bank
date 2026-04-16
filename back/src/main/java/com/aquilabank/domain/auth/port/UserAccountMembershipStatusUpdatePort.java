package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.UserAccountMembershipStatusUpdateCommand;
import com.aquilabank.domain.auth.model.UserAccountMembershipSummary;

/** 내부 auth 관리 경로의 membership status update를 저장소 계층으로 위임합니다. */
public interface UserAccountMembershipStatusUpdatePort {

  UserAccountMembershipSummary updateStatus(UserAccountMembershipStatusUpdateCommand command);
}
