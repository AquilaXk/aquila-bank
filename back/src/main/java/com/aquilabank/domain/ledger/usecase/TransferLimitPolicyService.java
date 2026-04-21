package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.exception.TransferLimitExceededException;
import com.aquilabank.domain.ledger.model.TransferCommand;
import com.aquilabank.domain.ledger.model.TransferLimitPolicy;
import com.aquilabank.domain.ledger.port.TransferLimitUsageReadPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/** source 계좌 기준 risk limit을 command write 전에 검증합니다. */
public final class TransferLimitPolicyService implements TransferLimitPolicyUseCase {

  private final TransferLimitUsageReadPort usageReadPort;
  private final Clock clock;
  private final ZoneId businessZoneId;
  private final TransferLimitPolicy policy;

  public TransferLimitPolicyService(
      TransferLimitUsageReadPort usageReadPort,
      Clock clock,
      ZoneId businessZoneId,
      TransferLimitPolicy policy) {
    this.usageReadPort = Objects.requireNonNull(usageReadPort, "usageReadPort");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.businessZoneId = Objects.requireNonNull(businessZoneId, "businessZoneId");
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  @Override
  public void validate(TransferCommand command) {
    if (command.amountMinor() > policy.singleTransferLimitMinor()) {
      throw new TransferLimitExceededException("single transfer limit exceeded");
    }
    Instant now = clock.instant();
    LocalDate businessDate = LocalDate.ofInstant(now, businessZoneId);
    Instant fromInclusive = businessDate.atStartOfDay(businessZoneId).toInstant();
    Instant toExclusive = businessDate.plusDays(1).atStartOfDay(businessZoneId).toInstant();
    long dailyUsed =
        usageReadPort.sumBookedDebitAmountMinor(
            command.sourceAccountId(), command.currencyCode(), fromInclusive, toExclusive);
    if (dailyUsed + command.amountMinor() > policy.dailyTransferLimitMinor()) {
      throw new TransferLimitExceededException("daily transfer limit exceeded");
    }
  }
}
