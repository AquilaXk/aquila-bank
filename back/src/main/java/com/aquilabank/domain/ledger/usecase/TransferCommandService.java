package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.TransferCommand;
import com.aquilabank.domain.ledger.model.TransferResult;
import com.aquilabank.domain.ledger.port.TransferWritePort;

public final class TransferCommandService implements TransferCommandUseCase {

  private final TransferWritePort transferWritePort;

  public TransferCommandService(TransferWritePort transferWritePort) {
    this.transferWritePort = transferWritePort;
  }

  @Override
  public TransferResult transfer(TransferCommand command) {
    return transferWritePort.transfer(command);
  }
}
