package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.UserAccountMembershipSummary;

public interface UserAccountMembershipQueryUseCase {

  UserAccountMembershipSummary getByUserIdAndAccountId(long userId, long accountId);
}
