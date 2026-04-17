package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.TransferReversalCommand;
import com.aquilabank.domain.ledger.model.TransferReversalResult;

/** 송금 reversal command 진입점 */
public interface TransferReversalUseCase {

  TransferReversalResult reverse(TransferReversalCommand command);
}
