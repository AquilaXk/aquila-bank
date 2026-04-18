package com.aquilabank.domain.auth.model;

/** 현재 JWT user가 자신의 특정 session 하나를 종료할 때 쓰는 최소 입력값입니다. */
public record AuthSessionRevokeCommand(long userId, long sessionId) {

  public AuthSessionRevokeCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (sessionId <= 0) {
      throw new IllegalArgumentException("sessionId must be positive");
    }
  }
}
