package com.aquilabank.domain.account.model;

/** 내부 운영 경로가 계좌 상태를 갱신할 때 필요한 최소 입력입니다. */
public record AccountStatusUpdateCommand(long accountId, AccountStatus status) {

  public AccountStatusUpdateCommand {
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    if (status == null) {
      throw new IllegalArgumentException("status is required");
    }
  }
}
