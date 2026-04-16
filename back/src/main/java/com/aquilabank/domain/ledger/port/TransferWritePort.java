package com.aquilabank.domain.ledger.port;

import com.aquilabank.domain.ledger.model.TransferCommand;
import com.aquilabank.domain.ledger.model.TransferResult;

public interface TransferWritePort {

  TransferResult transfer(TransferCommand command);
}
