package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.AuthUserSummary;
import java.util.Optional;

/** 내부 auth 관리 exact lookup을 위한 user 조회 port입니다. */
public interface UserQueryPort {

  Optional<AuthUserSummary> findSummaryByUserId(long userId);

  Optional<AuthUserSummary> findSummaryByLoginId(String loginId);
}
