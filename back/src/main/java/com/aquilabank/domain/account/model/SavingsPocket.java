package com.aquilabank.domain.account.model;

import com.aquilabank.standard.util.DomainGuards;
import java.time.Instant;
import java.util.Objects;

/** 계좌 내 목적별 목표 저축 포켓 모델입니다. */
public record SavingsPocket(
    long pocketId,
    long userId,
    long accountId,
    String pocketName,
    long targetAmountMinor,
    long savedAmountMinor,
    String currencyCode,
    Instant createdAt,
    Instant updatedAt) {

  public SavingsPocket {
    DomainGuards.requirePositive(pocketId, "pocketId");
    DomainGuards.requirePositive(userId, "userId");
    DomainGuards.requirePositive(accountId, "accountId");
    pocketName = DomainGuards.requireText(pocketName, 60, "pocketName");
    DomainGuards.requirePositive(targetAmountMinor, "targetAmountMinor");
    DomainGuards.requireNonNegative(savedAmountMinor, "savedAmountMinor");
    currencyCode = DomainGuards.requireCurrencyCode(currencyCode);
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }
}
