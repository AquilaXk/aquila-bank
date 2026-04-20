package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.account.model.AccountStatus;
import java.util.Optional;

/** bootstrap principal exact access 판단에 필요한 account status만 조회합니다. */
public interface AccountStatusAccessPort {

  Optional<AccountStatus> findAccountStatus(long accountId);
}
