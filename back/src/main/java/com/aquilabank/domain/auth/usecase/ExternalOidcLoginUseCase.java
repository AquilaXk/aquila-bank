package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.ExternalOidcLoginCommand;
import com.aquilabank.domain.auth.model.LoginResult;

public interface ExternalOidcLoginUseCase {

  LoginResult login(ExternalOidcLoginCommand command);
}
