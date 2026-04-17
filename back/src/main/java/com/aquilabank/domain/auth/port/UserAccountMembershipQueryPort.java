package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.UserAccountMembershipSummary;
import java.util.Optional;

/** 내부 auth 관리 exact lookup을 위한 membership 조회 port입니다. */
public interface UserAccountMembershipQueryPort {

  Optional<UserAccountMembershipSummary> findByUserIdAndAccountId(long userId, long accountId);
}
