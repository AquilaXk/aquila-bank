package com.aquilabank.domain.auth.exception;

/** 내부 auth 관리 경로에서 찾을 수 없는 membership 조회/갱신을 구분합니다. */
public class UserAccountMembershipNotFoundException extends RuntimeException {

  public UserAccountMembershipNotFoundException(String message) {
    super(message);
  }
}
