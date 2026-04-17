package com.aquilabank.domain.auth.model;

/** refresh token session lifecycle을 고정값으로 관리합니다. */
public enum RefreshTokenSessionStatus {
  ACTIVE,
  ROTATED,
  REVOKED
}
