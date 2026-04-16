package com.aquilabank.domain.ledger.exception;

public class CommandConflictException extends RuntimeException {

  public CommandConflictException(String message) {
    super(message);
  }
}
