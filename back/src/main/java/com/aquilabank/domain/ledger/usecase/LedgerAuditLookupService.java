package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.exception.LedgerAuditEntryNotFoundException;
import com.aquilabank.domain.ledger.model.LedgerAuditEntry;
import com.aquilabank.domain.ledger.port.LedgerAuditLookupPort;
import java.util.List;
import java.util.Objects;

public final class LedgerAuditLookupService implements LedgerAuditLookupUseCase {

  private final LedgerAuditLookupPort lookupPort;

  public LedgerAuditLookupService(LedgerAuditLookupPort lookupPort) {
    this.lookupPort = Objects.requireNonNull(lookupPort, "lookupPort");
  }

  @Override
  public List<LedgerAuditEntry> findByRequestId(String requestId, long afterEntryId, int limit) {
    validateRequired(requestId, "requestId");
    validateCursor(afterEntryId, limit);
    return lookupPort.findByRequestId(requestId, afterEntryId, limit);
  }

  @Override
  public List<LedgerAuditEntry> findByTransactionReference(
      String transactionReference, long afterEntryId, int limit) {
    validateRequired(transactionReference, "transactionReference");
    validateCursor(afterEntryId, limit);
    return lookupPort.findByTransactionReference(transactionReference, afterEntryId, limit);
  }

  @Override
  public LedgerAuditEntry getByEntryReference(String entryReference) {
    validateRequired(entryReference, "entryReference");
    return lookupPort
        .findByEntryReference(entryReference)
        .orElseThrow(
            () -> new LedgerAuditEntryNotFoundException("ledger audit entry is not found"));
  }

  private void validateRequired(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
  }

  private void validateCursor(long afterEntryId, int limit) {
    if (afterEntryId < 0) {
      throw new IllegalArgumentException("afterEntryId must not be negative");
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive");
    }
  }
}
