package com.aquilabank.domain.auth.model;

/** 내부 auth 상태 변경 사유 코드를 고정해 운영 분류를 흔들리지 않게 유지합니다. */
public enum AuthStatusChangeReasonCode {
  FRAUD_REVIEW,
  USER_REQUEST,
  OPS_MANUAL,
  ACCOUNT_CLOSURE,
  LEGACY_FREE_TEXT
}
