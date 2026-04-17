package com.aquilabank.domain.auth.exception;

/** user-account membership에 없는 계좌 접근을 막는 예외 */
public class AccountAccessDeniedException extends RuntimeException {

  public AccountAccessDeniedException(String message) {
    super(message);
  }
}
