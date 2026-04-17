package com.aquilabank.domain.auth.model;

/** 현재 user refresh token session 목록 조회 최소 입력값입니다. */
public record AuthSessionListQuery(long userId, int size) {

  public AuthSessionListQuery {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (size <= 0) {
      throw new IllegalArgumentException("size must be positive");
    }
    if (size > 50) {
      throw new IllegalArgumentException("size must be 50 or less");
    }
  }
}
