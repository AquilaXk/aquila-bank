package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.UserAccountMembershipStatusUpdateCommand;
import com.aquilabank.domain.auth.model.UserAccountMembershipSummary;

public interface UserAccountMembershipStatusUpdateUseCase {

  UserAccountMembershipSummary update(UserAccountMembershipStatusUpdateCommand command);
}
