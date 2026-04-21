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
    // 한도 초과는 command_idempotency insert 전 차단해 실패 side effect를 남기지 않습니다.
    limitPolicyUseCase.validate(command);
    // 실제 ledger 반영 책임은 write port 구현으로 위임
    return transferWritePort.transfer(command);
  }
}
