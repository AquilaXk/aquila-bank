package com.aquilabank.domain.auth.model;

/** 비로그인 password recovery 요청 입력입니다. */
public record PasswordRecoveryRequestCommand(String loginId) {

  public PasswordRecoveryRequestCommand {
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("loginId is required");
    }
    if (loginId.length() > 80) {
      throw new IllegalArgumentException("loginId must be 80 characters or less");
    }
  }
}
