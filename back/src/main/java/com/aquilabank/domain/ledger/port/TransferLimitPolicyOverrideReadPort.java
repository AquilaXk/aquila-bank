package com.aquilabank.domain.ledger.port;

import com.aquilabank.domain.ledger.model.TransferLimitPolicy;
import java.util.Optional;

public interface TransferLimitPolicyOverrideReadPort {

  Optional<TransferLimitPolicy> findByAccountId(long accountId);
}
