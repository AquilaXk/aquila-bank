package com.aquilabank.domain.auth.model;

/** 현재 JWT user가 자신의 active session 전체를 종료할 때 쓰는 최소 입력값입니다. */
public record AuthSessionRevokeAllCommand(long userId) {

  public AuthSessionRevokeAllCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
  }
}
