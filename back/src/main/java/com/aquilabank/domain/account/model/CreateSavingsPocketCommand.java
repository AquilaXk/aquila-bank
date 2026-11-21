package com.aquilabank.domain.account.model;

import com.aquilabank.standard.util.DomainGuards;

/** 목표 저축 포켓 생성 명령입니다. */
public record CreateSavingsPocketCommand(
    long userId,
    long accountId,
    String pocketName,
    long targetAmountMinor,
    long savedAmountMinor,
    String currencyCode) {

  public CreateSavingsPocketCommand {
    DomainGuards.requirePositive(userId, "userId");
    DomainGuards.requirePositive(accountId, "accountId");
    pocketName = DomainGuards.requireText(pocketName, 60, "pocketName");
    DomainGuards.requirePositive(targetAmountMinor, "targetAmountMinor");
    DomainGuards.requireNonNegative(savedAmountMinor, "savedAmountMinor");
    currencyCode = DomainGuards.requireCurrencyCode(currencyCode);
  }
}
