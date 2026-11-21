package com.aquilabank.domain.transaction.model;

import com.aquilabank.standard.util.DomainGuards;
import java.time.Instant;
import java.util.Objects;

/** 거래별 사용자 분류 및 메모 오버레이 모델입니다. */
public record TransactionAnnotation(
    long userId,
    long accountId,
    String transactionReference,
    TransactionCategory category,
    String memo,
    Instant updatedAt) {

  public TransactionAnnotation {
    DomainGuards.requirePositive(userId, "userId");
    DomainGuards.requirePositive(accountId, "accountId");
    transactionReference =
        DomainGuards.requireText(transactionReference, 80, "transactionReference");
    Objects.requireNonNull(category, "category must not be null");
    memo = DomainGuards.requireOptionalText(memo, 200, "memo");
    Objects.requireNonNull(updatedAt, "updatedAt must not be null");
  }
}
