package com.aquilabank.domain.auth.model;

/** 내부 auth 관리 경로가 membership 상태를 변경할 때 쓰는 명령입니다. */
public record UserAccountMembershipStatusUpdateCommand(
    long userId,
    long accountId,
    MembershipStatus status,
    String reason,
    String actorSubject,
    String requestId) {

  public UserAccountMembershipStatusUpdateCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    if (status == null) {
      throw new IllegalArgumentException("status is required");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("reason is required");
    }
    if (actorSubject == null || actorSubject.isBlank()) {
      throw new IllegalArgumentException("actorSubject is required");
    }
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId is required");
    }
  }
}
