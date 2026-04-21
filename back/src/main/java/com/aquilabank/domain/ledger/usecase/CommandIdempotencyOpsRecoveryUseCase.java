package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.CommandIdempotencyStaleRecoveryResult;

/** stale STARTED command idempotency row 수동 회수 진입점 */
public interface CommandIdempotencyOpsRecoveryUseCase {

  CommandIdempotencyStaleRecoveryResult recoverStaleStarted();
}
