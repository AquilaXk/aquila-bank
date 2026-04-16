package com.aquilabank.domain.ledger.exception;

public class SnapshotNotFoundException extends RuntimeException {

  public SnapshotNotFoundException(String message) {
    super(message);
  }
}
