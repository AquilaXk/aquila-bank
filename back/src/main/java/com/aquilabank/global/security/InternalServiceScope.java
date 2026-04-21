package com.aquilabank.global.security;

/** 내부 운영 API scope를 고정값으로 관리해 endpoint별 오남용을 줄입니다. */
public enum InternalServiceScope {
  ACCOUNT_BOOTSTRAP("internal:account-bootstrap"),
  ACCOUNT_ADMIN("internal:account-admin"),
  AUTH_BOOTSTRAP("internal:auth-bootstrap"),
  AUTH_ADMIN("internal:auth-admin"),
  OUTBOX_OPS("internal:outbox-ops"),
  LEDGER_OPS("internal:ledger-ops");

  private final String claimValue;

  InternalServiceScope(String claimValue) {
    this.claimValue = claimValue;
  }

  public String claimValue() {
    return claimValue;
  }
}
