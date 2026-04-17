package com.aquilabank.domain.auth.model;

/** 조회/송금 허용 범위를 role로 단순화해 membership 정책을 고정합니다. */
public enum MembershipRole {
  OWNER,
  MEMBER,
  VIEWER;

  public boolean allows(AccountAccessScope scope) {
    return switch (scope) {
      case READ -> true;
      case TRANSFER -> this == OWNER || this == MEMBER;
    };
  }
}
