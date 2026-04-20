package com.aquilabank.domain.auth.exception;

/** recovery handoff requestId 기반 token 조회 실패를 나타내는 예외입니다. */
public class PasswordRecoveryTokenNotFoundException extends RuntimeException {

  public PasswordRecoveryTokenNotFoundException(String message) {
    super(message);
  }
}
