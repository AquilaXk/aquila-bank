package com.aquilabank.domain.transaction.model;

import java.time.Instant;

/** 거래 타임라인용 read model projection */
public record TransactionSummary(
    long id,
    long accountId,
    String transactionReference,
    TransactionDirection direction,
    TransactionStatus status,
    long amountMinor,
    long balanceAfterMinor,
    String currencyCode,
    String summary,
    String counterpartyMaskedName,
    Instant bookedAt) {}
