package com.aquilabank.domain.auth.model;

/** password recovery delivery outbox 상태를 worker 전이 기준으로 고정합니다. */
public enum PasswordRecoveryDeliveryStatus {
  PENDING,
  SENDING,
  SENT,
  SKIPPED,
  FAILED,
  QUARANTINED
}
