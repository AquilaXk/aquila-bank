package com.aquilabank.domain.auth.model;

/** provider 미호출 완료 사유를 password recovery delivery 성공과 분리합니다. */
public enum PasswordRecoveryDeliverySkipReason {
  TOKEN_MISSING,
  TOKEN_NOT_PENDING,
  TOKEN_EXPIRED,
  PROVIDER_URL_MISSING
}
