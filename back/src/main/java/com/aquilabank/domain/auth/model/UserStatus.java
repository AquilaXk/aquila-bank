package com.aquilabank.domain.auth.model;

/** 계정 상태가 ACTIVE가 아니면 로그인 발급을 막습니다. */
public enum UserStatus {
  ACTIVE,
  LOCKED,
  DISABLED
}
