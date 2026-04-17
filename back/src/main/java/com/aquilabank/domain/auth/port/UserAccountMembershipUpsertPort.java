package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.UserAccountMembership;
import com.aquilabank.domain.auth.model.UserAccountMembershipUpsertCommand;

/** user-account membership 생성/갱신을 저장소 계층으로 위임합니다. */
public interface UserAccountMembershipUpsertPort {

  UserAccountMembership upsert(UserAccountMembershipUpsertCommand command);
}
