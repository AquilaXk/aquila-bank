package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.TransferCommand;

public interface TransferLimitPolicyUseCase {

  void validate(TransferCommand command);
}
