package com.aquilabank.domain.auth.model;

/** 로그인 자격 증명 입력 */
public record LoginCommand(String loginId, String password) {

  public LoginCommand {
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("loginId is required");
    }
    if (password == null || password.isBlank()) {
      throw new IllegalArgumentException("password is required");
    }
  }
}
