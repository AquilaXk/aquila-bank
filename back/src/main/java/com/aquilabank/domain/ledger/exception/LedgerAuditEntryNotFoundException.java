package com.aquilabank.domain.ledger.exception;

/** 내부 ledger audit lookup에서 entryReference 단건을 찾지 못한 상태 */
public class LedgerAuditEntryNotFoundException extends RuntimeException {

  public LedgerAuditEntryNotFoundException(String message) {
    super(message);
  }
}
