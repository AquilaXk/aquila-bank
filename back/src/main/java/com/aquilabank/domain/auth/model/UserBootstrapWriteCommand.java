package com.aquilabank.domain.auth.model;

/** hash 완료된 사용자 credential을 저장소 계층으로 넘기는 write 명령입니다. */
public record UserBootstrapWriteCommand(
    String loginId, String passwordHash, String displayName, UserStatus status) {

  public UserBootstrapWriteCommand {
    if (loginId == null || loginId.isBlank()) {
      throw new IllegalArgumentException("loginId is required");
    }
    if (passwordHash == null || passwordHash.isBlank()) {
      throw new IllegalArgumentException("passwordHash is required");
    }
    if (displayName == null || displayName.isBlank()) {
      throw new IllegalArgumentException("displayName is required");
    }
    if (status == null) {
      throw new IllegalArgumentException("status is required");
    }
  }
}
