package com.aquilabank.domain.auth.model;

/** 내부 auth 관리 경로가 user 상태를 변경할 때 쓰는 명령입니다. */
public record UserStatusUpdateCommand(long userId, UserStatus status) {

  public UserStatusUpdateCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (status == null) {
      throw new IllegalArgumentException("status is required");
    }
  }
}
