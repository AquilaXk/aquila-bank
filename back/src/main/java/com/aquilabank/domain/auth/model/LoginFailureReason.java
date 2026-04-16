package com.aquilabank.domain.auth.model;

/** 로그인 실패 원인을 고정값으로 남겨 운영 검색 조건을 안정화합니다. */
public enum LoginFailureReason {
  INVALID_CREDENTIALS,
  LOCKED_THRESHOLD_REACHED,
  ACCOUNT_TEMPORARILY_LOCKED,
  USER_STATUS_LOCKED,
  USER_DISABLED
}
