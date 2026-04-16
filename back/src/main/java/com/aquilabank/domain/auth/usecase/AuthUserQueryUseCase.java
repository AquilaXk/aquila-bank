package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.AuthUserSummary;

public interface AuthUserQueryUseCase {

  AuthUserSummary getByUserId(long userId);

  AuthUserSummary getByLoginId(String loginId);
}
