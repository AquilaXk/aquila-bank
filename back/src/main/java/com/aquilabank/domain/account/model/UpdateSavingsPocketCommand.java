package com.aquilabank.domain.account.model;

import com.aquilabank.standard.util.DomainGuards;

/** 목표 저축 포켓 수정 명령입니다. */
public record UpdateSavingsPocketCommand(
    long userId,
    long accountId,
    long pocketId,
    String pocketName,
    long targetAmountMinor,
    long savedAmountMinor) {

  public UpdateSavingsPocketCommand {
    DomainGuards.requirePositive(userId, "userId");
    DomainGuards.requirePositive(accountId, "accountId");
    DomainGuards.requirePositive(pocketId, "pocketId");
    pocketName = DomainGuards.requireText(pocketName, 60, "pocketName");
    DomainGuards.requirePositive(targetAmountMinor, "targetAmountMinor");
    DomainGuards.requireNonNegative(savedAmountMinor, "savedAmountMinor");
  }
}
