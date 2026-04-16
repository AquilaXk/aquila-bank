package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.LoginUser;
import java.util.Optional;

/** loginId 기준 credential 조회 port */
public interface UserCredentialLoadPort {

  Optional<LoginUser> findByLoginId(String loginId);
}
