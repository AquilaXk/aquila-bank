package com.aquilabank.domain.auth.model;

/** 내부 auth 409 응답 분기 기준을 free-text message에서 분리합니다. */
public enum InternalAuthConflictReasonCode {
  DUPLICATE_LOGIN_ID,
  DUPLICATE_EXTERNAL_IDENTITY_MAPPING,
  STATUS_TRANSITION_CONFLICT
}
