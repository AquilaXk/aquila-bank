package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.exception.TransferLimitExceededException;
import com.aquilabank.domain.ledger.model.TransferCommand;
import com.aquilabank.domain.ledger.model.TransferLimitPolicy;
import com.aquilabank.domain.ledger.port.TransferLimitPolicyOverrideReadPort;
import com.aquilabank.domain.ledger.port.TransferLimitUsageReadPort;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

/** source 계좌 기준 risk limit을 command write 전에 검증합니다. */
public final class TransferLimitPolicyService implements TransferLimitPolicyUseCase {

  private final TransferLimitUsageReadPort usageReadPort;
  private final TransferLimitPolicyOverrideReadPort overrideReadPort;
  private final Clock clock;
  private final ZoneId businessZoneId;
  private final TransferLimitPolicy policy;

  public TransferLimitPolicyService(
      TransferLimitUsageReadPort usageReadPort,
      TransferLimitPolicyOverrideReadPort overrideReadPort,
      Clock clock,
      ZoneId businessZoneId,
      TransferLimitPolicy policy) {
    this.usageReadPort = Objects.requireNonNull(usageReadPort, "usageReadPort");
    this.overrideReadPort = Objects.requireNonNull(overrideReadPort, "overrideReadPort");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.businessZoneId = Objects.requireNonNull(businessZoneId, "businessZoneId");
    this.policy = Objects.requireNonNull(policy, "policy");
  }

  @Override
  public void validate(TransferCommand command) {
    TransferLimitPolicy effectivePolicy = resolvePolicy(command.sourceAccountId());
    if (command.amountMinor() > effectivePolicy.singleTransferLimitMinor()) {
      throw new TransferLimitExceededException("single transfer limit exceeded");
    }
    Instant now = clock.instant();
    LocalDate businessDate = LocalDate.ofInstant(now, businessZoneId);
    Instant fromInclusive = businessDate.atStartOfDay(businessZoneId).toInstant();
    Instant toExclusive = businessDate.plusDays(1).atStartOfDay(businessZoneId).toInstant();
    long dailyUsed =
        usageReadPort.sumBookedDebitAmountMinor(
            command.sourceAccountId(), command.currencyCode(), fromInclusive, toExclusive);
    if (dailyUsed + command.amountMinor() > effectivePolicy.dailyTransferLimitMinor()) {
      throw new TransferLimitExceededException("daily transfer limit exceeded");
    }
  }

  @Override
  public TransferLimitPolicy resolvePolicy(long sourceAccountId) {
    Optional<TransferLimitPolicy> override = overrideReadPort.findByAccountId(sourceAccountId);
    return override == null ? policy : override.orElse(policy);
  }
}
