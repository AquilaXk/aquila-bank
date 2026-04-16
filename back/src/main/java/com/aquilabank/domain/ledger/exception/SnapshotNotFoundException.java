package com.aquilabank.domain.ledger.exception;

/** 계좌 snapshot 부재 예외 */
public class SnapshotNotFoundException extends RuntimeException {

  public SnapshotNotFoundException(String message) {
    super(message);
  }
}
