package com.aquilabank.global.web.ledger;

import com.aquilabank.domain.ledger.model.LedgerAuditEntry;
import java.time.Instant;

public record LedgerAuditEntryResponse(
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
    Instant createdAt) {

  static LedgerAuditEntryResponse from(LedgerAuditEntry item) {
    return new LedgerAuditEntryResponse(
        item.id(),
        item.accountId(),
        item.transactionReference(),
        item.entryReference(),
        item.direction(),
        item.entryStatus(),
        item.amountMinor(),
        item.currencyCode(),
        item.bookedAt(),
        item.occurredAt(),
        item.description(),
        item.traceId(),
        item.createdAt());
  }
}
