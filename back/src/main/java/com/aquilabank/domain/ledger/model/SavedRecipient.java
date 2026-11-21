package com.aquilabank.domain.ledger.model;

import com.aquilabank.standard.util.DomainGuards;
import java.time.Instant;
import java.util.Objects;

/** 사용자가 저장한 자주 쓰는 입금계좌(수취인) 모델입니다. */
public record SavedRecipient(
    long recipientId,
    long userId,
    String nickname,
    String institutionCode,
    String accountNumber,
    String currencyCode,
    boolean favorite,
    Instant createdAt,
    Instant updatedAt) {

  public SavedRecipient {
    DomainGuards.requirePositive(recipientId, "recipientId");
    DomainGuards.requirePositive(userId, "userId");
    nickname = DomainGuards.requireText(nickname, 60, "nickname");
    institutionCode = DomainGuards.requireText(institutionCode, 20, "institutionCode");
    accountNumber = DomainGuards.requireText(accountNumber, 40, "accountNumber");
    currencyCode = DomainGuards.requireCurrencyCode(currencyCode);
    Objects.requireNonNull(createdAt, "createdAt must not be null");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }
}
