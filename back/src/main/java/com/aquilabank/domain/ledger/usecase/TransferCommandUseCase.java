package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.TransferCommand;
import com.aquilabank.domain.ledger.model.TransferResult;

/** 송금 명령 진입 use case */
public interface TransferCommandUseCase {

  TransferResult transfer(TransferCommand command);
}
