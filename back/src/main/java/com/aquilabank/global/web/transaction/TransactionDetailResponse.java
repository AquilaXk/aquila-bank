package com.aquilabank.global.web.transaction;

import com.aquilabank.domain.transaction.model.TransactionDetail;
import java.time.Instant;

/** 거래 상세 endpoint 응답 모델 */
public record TransactionDetailResponse(
    long accountId,
    String transactionReference,
    String direction,
    String transactionStatus,
    long amountMinor,
    long balanceAfterMinor,
    String currencyCode,
    String summary,
    String counterpartyMaskedName,
    Instant bookedAt,
    String entryReference,
    String entryStatus,
    Instant occurredAt,
    String description) {

  static TransactionDetailResponse from(TransactionDetail item) {
    return new TransactionDetailResponse(
        item.accountId(),
        item.transactionReference(),
        item.direction().name(),
        item.transactionStatus().name(),
        item.amountMinor(),
        item.balanceAfterMinor(),
        item.currencyCode(),
        item.summary(),
        item.counterpartyMaskedName(),
        item.bookedAt(),
        item.entryReference(),
        item.entryStatus().name(),
        item.occurredAt(),
        item.description());
  }
}
