package com.aquilabank.domain.auth.exception;

/** 내부 auth 관리 경로에서 찾을 수 없는 user 조회/갱신을 구분합니다. */
public class AuthUserNotFoundException extends RuntimeException {

  public AuthUserNotFoundException(String message) {
    super(message);
  }
}
