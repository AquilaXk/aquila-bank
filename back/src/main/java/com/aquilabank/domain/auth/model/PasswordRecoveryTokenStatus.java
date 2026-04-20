package com.aquilabank.domain.auth.model;

/** password recovery token 상태를 고정값으로 관리합니다. */
public enum PasswordRecoveryTokenStatus {
  PENDING,
  USED,
  EXPIRED,
  SUPERSEDED
}
