package com.aquilabank.domain.ledger.usecase;

import com.aquilabank.domain.ledger.model.LedgerAuditEntry;
import java.util.List;

public interface LedgerAuditLookupUseCase {

  List<LedgerAuditEntry> findByRequestId(String requestId, long afterEntryId, int limit);

  List<LedgerAuditEntry> findByTransactionReference(
      String transactionReference, long afterEntryId, int limit);

  LedgerAuditEntry getByEntryReference(String entryReference);
}
