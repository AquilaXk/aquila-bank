package com.aquilabank.domain.ledger.exception;

/** 송금 한도 초과는 write side effect 없이 거절해야 하는 risk policy 위반입니다. */
public class TransferLimitExceededException extends RuntimeException {

  public TransferLimitExceededException(String message) {
    super(message);
  }
}
