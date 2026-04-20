package com.aquilabank.domain.auth.exception;

/** requestId 기반 recovery token 조회 실패를 나타내는 예외입니다. */
public class PasswordRecoveryTokenNotFoundException extends RuntimeException {

  public PasswordRecoveryTokenNotFoundException(String message) {
    super(message);
  }
}
