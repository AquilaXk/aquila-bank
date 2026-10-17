package com.aquilabank.domain.ledger.port;

import com.aquilabank.domain.ledger.model.TransferCommand;
import com.aquilabank.domain.ledger.model.TransferResult;
import java.util.function.Consumer;

/** 송금 write path 위임용 domain port */
public interface TransferWritePort {

  TransferResult transfer(TransferCommand command);

  default TransferResult transfer(
      TransferCommand command, Consumer<TransferCommand> limitValidator) {
    if (limitValidator != null) {
      limitValidator.accept(command);
    }
    return transfer(command);
  }
}
