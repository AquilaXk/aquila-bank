package com.aquilabank.domain.auth.model;

/** access 검증이 membership 상태와 user 상태를 한 번에 판단하도록 묶은 조회 모델입니다. */
public record AccountAccessMembership(
    long userId,
    long accountId,
    MembershipRole role,
    MembershipStatus membershipStatus,
    UserStatus userStatus) {

  public AccountAccessMembership {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    if (role == null) {
      throw new IllegalArgumentException("role is required");
    }
    if (membershipStatus == null) {
      throw new IllegalArgumentException("membershipStatus is required");
    }
    if (userStatus == null) {
      throw new IllegalArgumentException("userStatus is required");
    }
  }
}
