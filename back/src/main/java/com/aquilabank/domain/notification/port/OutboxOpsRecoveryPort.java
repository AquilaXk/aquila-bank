package com.aquilabank.domain.notification.port;

import java.time.Duration;
import java.time.Instant;

public interface OutboxOpsRecoveryPort {

  int recoverStaleSending(Duration staleAfter, Instant recoveredAt);
}
