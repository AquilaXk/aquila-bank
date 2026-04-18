package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.TotpLoginChallengeUpdateCommand;
import com.aquilabank.domain.auth.model.TotpLoginChallengeUpsertCommand;

public interface TotpLoginChallengeWritePort {

  void upsert(TotpLoginChallengeUpsertCommand command);

  void update(TotpLoginChallengeUpdateCommand command);
}
