package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.model.TotpChallengeVerifyCommand;

public interface TotpChallengeVerifyUseCase {

  LoginResult verify(TotpChallengeVerifyCommand command);
}
