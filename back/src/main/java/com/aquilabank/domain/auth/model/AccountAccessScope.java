package com.aquilabank.domain.auth.model;

import com.aquilabank.domain.account.model.AccountStatus;

/** 요청 경로별로 필요한 최소 계좌 권한 수준 */
public enum AccountAccessScope {
  READ,
  TRANSFER;

  /** `LOCKED`는 read만 허용하고, `CLOSED`는 모든 public access를 막습니다. */
  public boolean allows(AccountStatus accountStatus) {
    if (accountStatus == null) {
      throw new IllegalArgumentException("accountStatus is required");
    }
    return switch (accountStatus) {
      case ACTIVE -> true;
      case LOCKED -> this == READ;
      case CLOSED -> false;
    };
  }
}
