package com.aquilabank.domain.notification.usecase;

import com.aquilabank.domain.notification.model.OutboxStaleRecoveryResult;

public interface OutboxOpsRecoveryUseCase {

  OutboxStaleRecoveryResult recoverStaleSending();
}
