package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.TotpCredentialActivateCommand;
import com.aquilabank.domain.auth.model.TotpCredentialTouchCommand;
import com.aquilabank.domain.auth.model.TotpCredentialUpsertCommand;

public interface TotpCredentialWritePort {

  void upsertPending(TotpCredentialUpsertCommand command);

  void activate(TotpCredentialActivateCommand command);

  void touchLastUsed(TotpCredentialTouchCommand command);
}
