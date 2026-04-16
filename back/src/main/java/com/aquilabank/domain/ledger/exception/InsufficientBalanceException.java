package com.aquilabank.domain.ledger.exception;

/** 잔액 부족 예외 */
public class InsufficientBalanceException extends RuntimeException {

  public InsufficientBalanceException(String message) {
    super(message);
  }
}
