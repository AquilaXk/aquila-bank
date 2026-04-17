package com.aquilabank.domain.transaction.model;

import java.time.Instant;

/** 거래 상세 drill-down 응답용 domain projection */
public record TransactionDetail(
    long accountId,
    String transactionReference,
    TransactionDirection direction,
    TransactionStatus transactionStatus,
    long amountMinor,
    long balanceAfterMinor,
    String currencyCode,
    String summary,
    String counterpartyMaskedName,
    Instant bookedAt,
    String entryReference,
    TransactionStatus entryStatus,
    Instant occurredAt,
    String description) {}
