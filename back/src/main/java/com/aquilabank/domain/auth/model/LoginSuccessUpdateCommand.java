package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 성공 로그인 시 failure state reset과 마지막 성공 시각을 함께 남깁니다. */
public record LoginSuccessUpdateCommand(long userId, Instant succeededAt) {

  public LoginSuccessUpdateCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (succeededAt == null) {
      throw new IllegalArgumentException("succeededAt is required");
    }
  }
}
