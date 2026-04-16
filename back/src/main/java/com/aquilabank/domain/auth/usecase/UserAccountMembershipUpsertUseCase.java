package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.UserAccountMembership;
import com.aquilabank.domain.auth.model.UserAccountMembershipUpsertCommand;

public interface UserAccountMembershipUpsertUseCase {

  UserAccountMembership upsert(UserAccountMembershipUpsertCommand command);
}
