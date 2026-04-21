package com.aquilabank.domain.ledger.port;

import com.aquilabank.domain.ledger.model.LedgerAuditEntry;
import java.util.List;
import java.util.Optional;

public interface LedgerAuditLookupPort {

  List<LedgerAuditEntry> findByRequestId(String requestId, long afterEntryId, int limit);

  List<LedgerAuditEntry> findByTransactionReference(
      String transactionReference, long afterEntryId, int limit);

  Optional<LedgerAuditEntry> findByEntryReference(String entryReference);
}
