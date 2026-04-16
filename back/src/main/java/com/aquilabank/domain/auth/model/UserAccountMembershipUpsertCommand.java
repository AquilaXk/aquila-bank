package com.aquilabank.domain.auth.model;

/** 내부 bootstrap에서 user-account membership을 생성/갱신할 때 쓰는 명령입니다. */
public record UserAccountMembershipUpsertCommand(
    long userId, long accountId, MembershipRole role, MembershipStatus status) {

  public UserAccountMembershipUpsertCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    if (role == null) {
      throw new IllegalArgumentException("role is required");
    }
    if (status == null) {
      throw new IllegalArgumentException("status is required");
    }
  }
}
