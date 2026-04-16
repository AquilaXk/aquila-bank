package com.aquilabank.domain.auth.model;

/** 내부 bootstrap에서 새 사용자를 만들 때 받는 최소 입력입니다. */
public record UserBootstrapCommand(String loginId, String password, String displayName) {

  public UserBootstrapCommand {
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("loginId is required");
    }
    if (password == null || password.isBlank()) {
      throw new IllegalArgumentException("password is required");
    }
    if (displayName == null || displayName.isBlank()) {
      throw new IllegalArgumentException("displayName is required");
    }
  }
}
