package com.aquilabank.domain.auth.model;

/** 민감 mutation 실행 전 현재 access token의 session이 유효한지 확인하는 command입니다. */
public record CurrentSessionActiveCheckCommand(
    long userId, long sessionId, String requestId, String method, String path) {

  public CurrentSessionActiveCheckCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (sessionId <= 0) {
      throw new IllegalArgumentException("sessionId must be positive");
    }
    requestId = requireText(requestId, "requestId");
    method = requireText(method, "method");
    path = requireText(path, "path");
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " must not be blank");
    }
    return value;
  }
}
