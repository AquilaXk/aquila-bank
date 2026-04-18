package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.TotpCredential;
import java.util.Optional;

public interface TotpCredentialLoadPort {

  Optional<TotpCredential> findCredentialByUserId(long userId);

  Optional<TotpCredential> findCredentialByUserIdForUpdate(long userId);
}
