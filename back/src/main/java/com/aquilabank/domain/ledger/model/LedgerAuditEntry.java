package com.aquilabank.domain.ledger.model;

import java.time.Instant;

public record LedgerAuditEntry(
    long id,
    long accountId,
    String transactionReference,
    String entryReference,
    String direction,
    String entryStatus,
    long amountMinor,
    String currencyCode,
    Instant bookedAt,
    Instant occurredAt,
    String description,
    String traceId,
    Instant createdAt) {}
