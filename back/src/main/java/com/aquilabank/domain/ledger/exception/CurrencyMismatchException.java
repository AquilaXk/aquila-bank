package com.aquilabank.domain.ledger.exception;

/** 송금 당사자 통화 불일치 예외 */
public class CurrencyMismatchException extends RuntimeException {

  public CurrencyMismatchException(String message) {
    super(message);
  }
}
