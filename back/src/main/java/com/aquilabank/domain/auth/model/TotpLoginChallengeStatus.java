package com.aquilabank.domain.auth.model;

public enum TotpLoginChallengeStatus {
  PENDING,
  VERIFIED,
  FAILED,
  EXPIRED
}
