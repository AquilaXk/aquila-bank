package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.UserBootstrapCommand;
import com.aquilabank.domain.auth.model.UserBootstrapResult;

public interface UserBootstrapUseCase {

  UserBootstrapResult bootstrap(UserBootstrapCommand command);
}
