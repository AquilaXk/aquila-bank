package com.aquilabank.domain.ledger.port;

import com.aquilabank.domain.ledger.model.CreateScheduledTransferCommand;
import com.aquilabank.domain.ledger.model.ScheduledTransferRule;
import com.aquilabank.domain.ledger.model.ScheduledTransferStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ScheduledTransferPort {

  List<ScheduledTransferRule> findActiveAndPausedByUserId(long userId, int limit);

  Optional<ScheduledTransferRule> findByUserAndId(long userId, long scheduleId);

  int countActiveAndPausedByUserId(long userId);

  ScheduledTransferRule insert(CreateScheduledTransferCommand command, Instant now);

  Optional<ScheduledTransferRule> updateStatus(
      long userId, long scheduleId, ScheduledTransferStatus nextStatus, Instant now);
}
