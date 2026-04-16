package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.LoginCommand;
import com.aquilabank.domain.auth.model.LoginResult;

/** 사용자 로그인과 bearer token 발급 진입점 */
public interface LoginUseCase {

  LoginResult login(LoginCommand command);
}
