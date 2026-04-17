package com.aquilabank.domain.auth.usecase;

import com.aquilabank.domain.auth.exception.AuthUserNotFoundException;
import com.aquilabank.domain.auth.model.AuthUserSummary;
import com.aquilabank.domain.auth.port.UserQueryPort;

/** 내부 auth 관리 exact lookup을 domain 예외와 함께 묶습니다. */
public final class AuthUserQueryService implements AuthUserQueryUseCase {

  private final UserQueryPort userQueryPort;

  public AuthUserQueryService(UserQueryPort userQueryPort) {
    this.userQueryPort = userQueryPort;
  }

  @Override
  public AuthUserSummary getByUserId(long userId) {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    return userQueryPort
        .findSummaryByUserId(userId)
        .orElseThrow(() -> new AuthUserNotFoundException("user is not found"));
  }

  @Override
  public AuthUserSummary getByLoginId(String loginId) {
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("loginId is required");
    }
    return userQueryPort
        .findSummaryByLoginId(loginId)
        .orElseThrow(() -> new AuthUserNotFoundException("user is not found"));
  }
}
