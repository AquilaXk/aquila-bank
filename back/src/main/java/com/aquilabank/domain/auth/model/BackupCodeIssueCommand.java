package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 새 backup code row 저장 최소 입력값입니다. */
public record BackupCodeIssueCommand(long userId, String codeHash, Instant createdAt) {

  public BackupCodeIssueCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (codeHash == null || codeHash.isBlank()) {
      throw new IllegalArgumentException("codeHash is required");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt is required");
    }
  }
}
