package com.aquilabank.domain.ledger.exception;

public final class ScheduledTransferNotFoundException extends RuntimeException {

  public ScheduledTransferNotFoundException(String message) {
    super(message);
  }
}
