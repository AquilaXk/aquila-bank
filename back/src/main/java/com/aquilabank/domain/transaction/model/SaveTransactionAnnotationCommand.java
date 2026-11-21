package com.aquilabank.domain.transaction.model;

import com.aquilabank.standard.util.DomainGuards;
import java.util.Objects;

/** 거래 분류 및 메모 저장 명령입니다. */
public record SaveTransactionAnnotationCommand(
    long userId,
    long accountId,
    String transactionReference,
    TransactionCategory category,
    String memo) {

  public SaveTransactionAnnotationCommand {
    DomainGuards.requirePositive(userId, "userId");
    DomainGuards.requirePositive(accountId, "accountId");
    transactionReference =
        DomainGuards.requireText(transactionReference, 80, "transactionReference");
    Objects.requireNonNull(category, "category must not be null");
    memo = DomainGuards.requireOptionalText(memo, 200, "memo");
  }
}
