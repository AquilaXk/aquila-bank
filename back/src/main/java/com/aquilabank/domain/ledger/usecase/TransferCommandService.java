package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.TransferCommand;
import com.aquilabank.domain.ledger.model.TransferResult;
import com.aquilabank.domain.ledger.port.TransferWritePort;
import java.util.Objects;

/** 송금 명령을 한도 policy 검증 후 write port로 위임하는 use case 구현 */
public final class TransferCommandService implements TransferCommandUseCase {

  private final TransferLimitPolicyUseCase limitPolicyUseCase;
  private final TransferWritePort transferWritePort;

  public TransferCommandService(
      TransferLimitPolicyUseCase limitPolicyUseCase, TransferWritePort transferWritePort) {
    this.limitPolicyUseCase = Objects.requireNonNull(limitPolicyUseCase, "limitPolicyUseCase");
    this.transferWritePort = Objects.requireNonNull(transferWritePort, "transferWritePort");
  }

  @Override
  public TransferResult transfer(TransferCommand command) {
    // COMPLETED 멱등 재시도는 한도 검사 전에 즉시 복원하고, 신규/동시 요청은 락 전후로 한도를 검증합니다.
    return transferWritePort.transfer(command, limitPolicyUseCase::validate);
  }
}
