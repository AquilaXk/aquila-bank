package com.aquilabank.domain.ledger.model;

import com.aquilabank.standard.util.DomainGuards;

/** 자주 쓰는 입금계좌(수취인) 등록·갱신 명령입니다. */
public record SaveSavedRecipientCommand(
    long userId,
    String nickname,
    String institutionCode,
    String accountNumber,
    String currencyCode,
    boolean favorite) {

  public SaveSavedRecipientCommand {
    DomainGuards.requirePositive(userId, "userId");
    nickname = DomainGuards.requireText(nickname, 60, "nickname");
    institutionCode = DomainGuards.requireText(institutionCode, 20, "institutionCode");
    accountNumber = DomainGuards.requireText(accountNumber, 40, "accountNumber");
    currencyCode = DomainGuards.requireCurrencyCode(currencyCode);
  }
}
