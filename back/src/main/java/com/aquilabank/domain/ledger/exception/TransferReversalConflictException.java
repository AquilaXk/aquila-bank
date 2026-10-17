package com.aquilabank.domain.ledger.exception;

/** 이미 역분개(reversal)로 생성된 거래를 다시 역분개하려 할 때 발생하는 충돌 예외 */
public class TransferReversalConflictException extends CommandConflictException {

  public TransferReversalConflictException(String message) {
    super(message);
  }
}
