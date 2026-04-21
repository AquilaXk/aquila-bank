package com.aquilabank.global.web.ledger;

import com.aquilabank.domain.ledger.model.LedgerAuditEntry;
import java.util.List;

public record LedgerAuditEntryListResponse(
    String lookupType,
    String lookupValue,
    long afterEntryId,
    int limit,
    long nextAfterEntryId,
    List<LedgerAuditEntryResponse> items) {

  static LedgerAuditEntryListResponse from(
      String lookupType,
      String lookupValue,
      long afterEntryId,
      int limit,
      List<LedgerAuditEntry> items) {
    List<LedgerAuditEntryResponse> responses =
        items.stream().map(LedgerAuditEntryResponse::from).toList();
    long nextAfterEntryId = items.isEmpty() ? afterEntryId : items.getLast().id();
    return new LedgerAuditEntryListResponse(
        lookupType, lookupValue, afterEntryId, limit, nextAfterEntryId, responses);
  }
}
