package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.CurrentSessionActiveCheckCommand;

public interface CurrentSessionActiveUseCase {

  void requireActive(CurrentSessionActiveCheckCommand command);
}
