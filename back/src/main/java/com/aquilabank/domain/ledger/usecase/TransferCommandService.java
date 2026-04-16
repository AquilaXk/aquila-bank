package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.TransferCommand;
import com.aquilabank.domain.ledger.model.TransferResult;
import com.aquilabank.domain.ledger.port.TransferWritePort;

/** 송금 명령을 write port로 위임하는 기본 use case 구현 */
public final class TransferCommandService implements TransferCommandUseCase {

  private final TransferWritePort transferWritePort;

  public TransferCommandService(TransferWritePort transferWritePort) {
    this.transferWritePort = transferWritePort;
  }

  @Override
  public TransferResult transfer(TransferCommand command) {
    // 실제 ledger 반영 책임은 write port 구현으로 위임
    return transferWritePort.transfer(command);
  }
}
