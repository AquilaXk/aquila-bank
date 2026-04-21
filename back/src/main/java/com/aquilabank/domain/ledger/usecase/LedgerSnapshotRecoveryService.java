package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.LedgerSnapshotRecoveryCommand;
import com.aquilabank.domain.ledger.model.LedgerSnapshotRecoveryResult;
import com.aquilabank.domain.ledger.port.LedgerSnapshotRecoveryPort;
import java.time.Clock;
import java.util.Objects;

public final class LedgerSnapshotRecoveryService implements LedgerSnapshotRecoveryUseCase {

  private final LedgerSnapshotRecoveryPort recoveryPort;
  private final Clock clock;

  public LedgerSnapshotRecoveryService(LedgerSnapshotRecoveryPort recoveryPort, Clock clock) {
    this.recoveryPort = Objects.requireNonNull(recoveryPort, "recoveryPort");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  @Override
  public LedgerSnapshotRecoveryResult recoverSnapshot(LedgerSnapshotRecoveryCommand command) {
    return recoveryPort.recoverSnapshot(command, clock.instant());
  }
}
