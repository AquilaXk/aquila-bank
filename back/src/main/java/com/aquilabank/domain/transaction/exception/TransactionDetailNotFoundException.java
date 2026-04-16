package com.aquilabank.domain.transaction.exception;

/** 거래 상세 exact lookup 결과 부재 예외 */
public class TransactionDetailNotFoundException extends RuntimeException {

  public TransactionDetailNotFoundException(String message) {
    super(message);
  }
}
