package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.TransferReversalCommand;
import com.aquilabank.domain.ledger.model.TransferReversalResult;
import com.aquilabank.domain.ledger.port.TransferReversalWritePort;

/** reversal use case는 write port에 위임하고 adapter 경계를 유지합니다. */
public final class TransferReversalCommandService implements TransferReversalUseCase {

  private final TransferReversalWritePort transferReversalWritePort;

  public TransferReversalCommandService(TransferReversalWritePort transferReversalWritePort) {
    this.transferReversalWritePort = transferReversalWritePort;
  }

  @Override
  public TransferReversalResult reverse(TransferReversalCommand command) {
    return transferReversalWritePort.reverse(command);
  }
}
