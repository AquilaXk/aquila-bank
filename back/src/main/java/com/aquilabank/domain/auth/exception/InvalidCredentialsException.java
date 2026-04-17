package com.aquilabank.domain.auth.exception;

/** 로그인 자격 증명 검증 실패를 나타내는 예외 */
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException(String message) {
    super(message);
  }
}
