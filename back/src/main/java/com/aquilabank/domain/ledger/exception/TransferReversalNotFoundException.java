package com.aquilabank.domain.ledger.exception;

/** reversal 대상 원본 transfer를 찾지 못했을 때 반환하는 예외 */
public final class TransferReversalNotFoundException extends RuntimeException {

  public TransferReversalNotFoundException(String message) {
    super(message);
  }
}
