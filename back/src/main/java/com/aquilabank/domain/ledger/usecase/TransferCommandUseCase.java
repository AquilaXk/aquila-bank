package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.TransferCommand;
import com.aquilabank.domain.ledger.model.TransferResult;

public interface TransferCommandUseCase {

  TransferResult transfer(TransferCommand command);
}
