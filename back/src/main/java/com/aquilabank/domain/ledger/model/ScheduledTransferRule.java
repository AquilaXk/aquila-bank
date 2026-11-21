package com.aquilabank.domain.ledger.model;

import com.aquilabank.standard.util.DomainGuards;
import java.time.Instant;
import java.util.Objects;

/** 예약이체 및 정기 자동이체 규칙 모델입니다. */
public record ScheduledTransferRule(
    long scheduleId,
    long userId,
    long sourceAccountId,
    String targetAccountNumber,
    long amountMinor,
    String currencyCode,
    String summary,
    ScheduledTransferFrequency scheduleType,
    int scheduledDay,
    Instant nextExecutionAt,
    ScheduledTransferStatus ruleStatus,
    Instant createdAt,
    Instant updatedAt) {

  public ScheduledTransferRule {
    DomainGuards.requirePositive(scheduleId, "scheduleId");
    DomainGuards.requirePositive(userId, "userId");
    DomainGuards.requirePositive(sourceAccountId, "sourceAccountId");
    targetAccountNumber = DomainGuards.requireText(targetAccountNumber, 40, "targetAccountNumber");
    DomainGuards.requirePositive(amountMinor, "amountMinor");
    currencyCode = DomainGuards.requireCurrencyCode(currencyCode);
    summary = DomainGuards.requireText(summary, 120, "summary");
    Objects.requireNonNull(scheduleType, "scheduleType must not be null");
    if (scheduledDay < 1 || scheduledDay > 31) {
      throw new IllegalArgumentException("scheduledDay must be between 1 and 31");
    }
    Objects.requireNonNull(nextExecutionAt, "nextExecutionAt must not be null");
    Objects.requireNonNull(ruleStatus, "ruleStatus must not be null");
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }
}
