package com.aquilabank.domain.ledger.exception;

public class CurrencyMismatchException extends RuntimeException {

  public CurrencyMismatchException(String message) {
    super(message);
  }
}
