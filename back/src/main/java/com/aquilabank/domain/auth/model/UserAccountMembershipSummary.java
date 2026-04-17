package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 내부 auth 관리 조회가 membership 상태와 갱신 시각을 확인할 때 쓰는 요약 모델입니다. */
public record UserAccountMembershipSummary(
    long userId,
    long accountId,
    MembershipRole role,
    MembershipStatus status,
    Instant createdAt,
    Instant updatedAt) {

  public UserAccountMembershipSummary {
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
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt is required");
    }
    if (updatedAt == null) {
      throw new IllegalArgumentException("updatedAt is required");
    }
  }
}
