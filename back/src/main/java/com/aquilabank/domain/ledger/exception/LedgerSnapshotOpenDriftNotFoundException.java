package com.aquilabank.domain.ledger.exception;

/** snapshot recovery는 reconciliation으로 확인된 open drift만 대상으로 제한합니다. */
public class LedgerSnapshotOpenDriftNotFoundException extends RuntimeException {

  public LedgerSnapshotOpenDriftNotFoundException(String message) {
    super(message);
  }
}
