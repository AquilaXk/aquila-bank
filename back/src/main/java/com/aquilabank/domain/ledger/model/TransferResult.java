package com.aquilabank.domain.ledger.model;

import java.time.Instant;

public record TransferResult(
    String transactionReference,
    long sourceAccountId,
    long targetAccountId,
    long amountMinor,
    String currencyCode,
    long availableBalanceAfterMinor,
    Instant bookedAt,
    String status) {}
