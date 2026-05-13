package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.TransferCommand;
import com.aquilabank.domain.ledger.model.TransferLimitPolicy;

public interface TransferLimitPolicyUseCase {

  void validate(TransferCommand command);

  TransferLimitPolicy resolvePolicy(long sourceAccountId);
}
