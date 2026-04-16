package com.aquilabank.domain.transaction.model;

import java.time.Instant;

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
