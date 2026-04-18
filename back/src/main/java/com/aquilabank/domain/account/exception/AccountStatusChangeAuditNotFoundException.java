package com.aquilabank.domain.account.exception;

/** 계좌 상태 변경 감사 row 부재 예외 */
public class AccountStatusChangeAuditNotFoundException extends RuntimeException {

  public AccountStatusChangeAuditNotFoundException(String message) {
    super(message);
  }
}
