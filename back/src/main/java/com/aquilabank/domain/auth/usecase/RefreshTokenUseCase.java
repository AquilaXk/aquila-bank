package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.model.RefreshTokenCommand;

/** refresh token으로 새 token pair를 재발급합니다. */
public interface RefreshTokenUseCase {

  LoginResult refresh(RefreshTokenCommand command);
}
