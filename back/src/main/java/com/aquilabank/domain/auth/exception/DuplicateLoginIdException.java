package com.aquilabank.domain.auth.exception;

/** 같은 loginId 재사용을 막아 내부 bootstrap 중복 생성을 방지합니다. */
public class DuplicateLoginIdException extends RuntimeException {

  public DuplicateLoginIdException(String message) {
    super(message);
  }
}
