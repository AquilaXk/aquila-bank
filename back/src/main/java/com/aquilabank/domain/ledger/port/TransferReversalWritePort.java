package com.aquilabank.domain.ledger.port;

import com.aquilabank.domain.ledger.model.TransferReversalCommand;
import com.aquilabank.domain.ledger.model.TransferReversalResult;

/** reversal command 쓰기 경로를 ledger adapter에 위임합니다. */
public interface TransferReversalWritePort {

  TransferReversalResult reverse(TransferReversalCommand command);
}
