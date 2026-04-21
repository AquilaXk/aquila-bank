package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.LoginUser;
import java.util.Optional;

/** 외부 provider subject에 명시 연결된 내부 user만 조회합니다. */
public interface ExternalIdentityUserLoadPort {

  Optional<LoginUser> findByProviderIdAndSubjectForUpdate(String providerId, String subject);
}
