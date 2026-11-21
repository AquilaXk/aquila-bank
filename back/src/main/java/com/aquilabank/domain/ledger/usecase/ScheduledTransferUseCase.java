package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.CreateScheduledTransferCommand;
import com.aquilabank.domain.ledger.model.ScheduledTransferRule;
import com.aquilabank.domain.ledger.model.ScheduledTransferStatus;
import java.util.List;

public interface ScheduledTransferUseCase {

  List<ScheduledTransferRule> list(long userId);

  ScheduledTransferRule get(long userId, long scheduleId);

  ScheduledTransferRule create(CreateScheduledTransferCommand command);

  ScheduledTransferRule updateStatus(
      long userId, long scheduleId, ScheduledTransferStatus nextStatus);
}
