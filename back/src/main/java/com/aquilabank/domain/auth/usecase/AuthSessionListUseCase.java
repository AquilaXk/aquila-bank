package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.AuthSessionList;
import com.aquilabank.domain.auth.model.AuthSessionListQuery;

public interface AuthSessionListUseCase {

  AuthSessionList get(AuthSessionListQuery query);
}
