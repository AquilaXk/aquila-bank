package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.AccountAccessMembership;
import com.aquilabank.domain.auth.model.UserAccountMembership;
import java.util.Optional;

/** user-account membership을 조회해 계좌 접근 가능 여부를 계산하는 port */
public interface AccountAccessPort {

  Optional<UserAccountMembership> findMembership(long userId, long accountId);

  Optional<AccountAccessMembership> findAccessMembership(long userId, long accountId);
}
