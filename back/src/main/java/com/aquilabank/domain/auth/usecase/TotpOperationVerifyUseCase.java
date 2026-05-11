package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.TotpOperationVerifyCommand;

public interface TotpOperationVerifyUseCase {

  void verify(TotpOperationVerifyCommand command);
}
