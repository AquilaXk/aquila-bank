package com.aquilabank.domain.ledger.exception;

/** idempotency 충돌 또는 중복 명령 감지 예외 */
public class CommandConflictException extends RuntimeException {

  public CommandConflictException(String message) {
    super(message);
  }
}
