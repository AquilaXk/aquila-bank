package com.aquilabank.domain.auth.model;

/** revoked membership는 남겨도 접근 계산에서는 제외합니다. */
public enum MembershipStatus {
  ACTIVE,
  REVOKED
}
