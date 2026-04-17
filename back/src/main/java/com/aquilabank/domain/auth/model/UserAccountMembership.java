package com.aquilabank.domain.auth.model;

/** user와 account를 연결하는 단건 membership 조회 모델 */
public record UserAccountMembership(
    long userId, long accountId, MembershipRole role, MembershipStatus status) {

  public UserAccountMembership {
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
