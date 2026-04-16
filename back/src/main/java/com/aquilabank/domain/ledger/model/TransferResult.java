package com.aquilabank.domain.ledger.model;

import java.time.Instant;

/** 송금 완료 결과 모델 */
public record TransferResult(
    String transactionReference,
    long sourceAccountId,
    long targetAccountId,
    long amountMinor,
    String currencyCode,
    long availableBalanceAfterMinor,
    Instant bookedAt,
    String status) {}
