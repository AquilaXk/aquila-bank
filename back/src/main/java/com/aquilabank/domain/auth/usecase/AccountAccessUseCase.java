package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.model.AccountAccessScope;

/** user가 특정 account에 필요한 권한을 가졌는지 확인하는 진입점 */
public interface AccountAccessUseCase {

  long verify(long userId, long accountId, AccountAccessScope scope);

  long verifyBootstrapAccount(long accountId, AccountAccessScope scope);
}
