package com.aquilabank.domain.ledger.port;

import com.aquilabank.domain.ledger.model.TransferCommand;
import com.aquilabank.domain.ledger.model.TransferResult;

/** 송금 write path 위임용 domain port */
public interface TransferWritePort {

  TransferResult transfer(TransferCommand command);
}
