package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.LedgerSnapshotDriftRecord;
import com.aquilabank.domain.ledger.port.LedgerSnapshotDriftReadPort;
import java.util.List;
import java.util.Objects;

public final class LedgerSnapshotDriftQueryService implements LedgerSnapshotDriftQueryUseCase {

  private final LedgerSnapshotDriftReadPort readPort;

  public LedgerSnapshotDriftQueryService(LedgerSnapshotDriftReadPort readPort) {
    this.readPort = Objects.requireNonNull(readPort, "readPort");
  }

  @Override
  public List<LedgerSnapshotDriftRecord> findOpenDrifts(long afterAccountId, int limit) {
    if (afterAccountId < 0) {
      throw new IllegalArgumentException("afterAccountId must not be negative");
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
    return readPort.findOpenDrifts(afterAccountId, limit);
  }
}
