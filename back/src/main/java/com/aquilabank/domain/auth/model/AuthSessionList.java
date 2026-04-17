package com.aquilabank.domain.auth.model;

import java.util.List;

/** 현재 user가 확인할 refresh token session 목록 응답 모델입니다. */
public record AuthSessionList(List<AuthSessionSummary> items) {

  public AuthSessionList {
    if (items == null) {
      throw new IllegalArgumentException("items is required");
    }
    items = List.copyOf(items);
  }
}
