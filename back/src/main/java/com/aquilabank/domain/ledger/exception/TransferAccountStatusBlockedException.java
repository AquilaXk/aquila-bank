package com.aquilabank.domain.ledger.exception;

public class TransferAccountStatusBlockedException extends RuntimeException {

  public TransferAccountStatusBlockedException(String message) {
    super(message);
  }
}
