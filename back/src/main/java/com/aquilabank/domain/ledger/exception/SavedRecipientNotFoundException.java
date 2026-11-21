package com.aquilabank.domain.ledger.exception;

public final class SavedRecipientNotFoundException extends RuntimeException {

  public SavedRecipientNotFoundException(String message) {
    super(message);
  }
}
