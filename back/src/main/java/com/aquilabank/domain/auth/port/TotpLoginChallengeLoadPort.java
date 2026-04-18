package com.aquilabank.domain.auth.port;

import com.aquilabank.domain.auth.model.TotpLoginChallenge;
import java.util.Optional;

public interface TotpLoginChallengeLoadPort {

  Optional<TotpLoginChallenge> findByChallengeIdForUpdate(String challengeId);
}
