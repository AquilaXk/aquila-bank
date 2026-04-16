package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.UserBootstrapResult;
import com.aquilabank.domain.auth.model.UserBootstrapWriteCommand;

/** hash 완료된 사용자 정보를 저장해 login 대상 user를 생성합니다. */
public interface UserBootstrapPort {

  UserBootstrapResult bootstrap(UserBootstrapWriteCommand command);
}
