package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.exception.ScheduledTransferNotFoundException;
import com.aquilabank.domain.ledger.model.CreateScheduledTransferCommand;
import com.aquilabank.domain.ledger.model.ScheduledTransferRule;
import com.aquilabank.domain.ledger.model.ScheduledTransferStatus;
import com.aquilabank.domain.ledger.port.ScheduledTransferPort;
import com.aquilabank.standard.util.DomainGuards;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** 예약이체 및 정기 자동이체 규칙 관리 유스케이스입니다. */
public final class ScheduledTransferService implements ScheduledTransferUseCase {

  public static final int MAX_SCHEDULES_PER_USER = 50;

  private final ScheduledTransferPort scheduledTransferPort;
  private final Clock clock;

  public ScheduledTransferService(ScheduledTransferPort scheduledTransferPort, Clock clock) {
    this.scheduledTransferPort = Objects.requireNonNull(scheduledTransferPort);
    this.clock = Objects.requireNonNull(clock);
  }

  @Override
  public List<ScheduledTransferRule> list(long userId) {
    DomainGuards.requirePositive(userId, "userId");
    return scheduledTransferPort.findActiveAndPausedByUserId(userId, MAX_SCHEDULES_PER_USER);
  }

  @Override
  public ScheduledTransferRule get(long userId, long scheduleId) {
    DomainGuards.requirePositive(userId, "userId");
    DomainGuards.requirePositive(scheduleId, "scheduleId");
    return scheduledTransferPort
        .findByUserAndId(userId, scheduleId)
        .orElseThrow(
            () -> new ScheduledTransferNotFoundException("scheduled transfer rule not found"));
  }

  @Override
  public ScheduledTransferRule create(CreateScheduledTransferCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    Instant now = Instant.now(clock);
    if (command.nextExecutionAt().isBefore(now.minusSeconds(60))) {
      throw new IllegalArgumentException("nextExecutionAt must not be in the past");
    }
    if (scheduledTransferPort.countActiveAndPausedByUserId(command.userId())
        >= MAX_SCHEDULES_PER_USER) {
      throw new IllegalArgumentException("scheduled transfer rule limit exceeded");
    }
    return scheduledTransferPort.insert(command, now);
  }

  @Override
  public ScheduledTransferRule updateStatus(
      long userId, long scheduleId, ScheduledTransferStatus nextStatus) {
    DomainGuards.requirePositive(userId, "userId");
    DomainGuards.requirePositive(scheduleId, "scheduleId");
    Objects.requireNonNull(nextStatus, "nextStatus must not be null");
    return scheduledTransferPort
        .updateStatus(userId, scheduleId, nextStatus, Instant.now(clock))
        .orElseThrow(
            () -> new ScheduledTransferNotFoundException("scheduled transfer rule not found"));
  }
}
