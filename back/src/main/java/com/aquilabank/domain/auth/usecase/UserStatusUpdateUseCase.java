package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.AuthUserSummary;
import com.aquilabank.domain.auth.model.UserStatusUpdateCommand;

public interface UserStatusUpdateUseCase {

  AuthUserSummary update(UserStatusUpdateCommand command);
}
