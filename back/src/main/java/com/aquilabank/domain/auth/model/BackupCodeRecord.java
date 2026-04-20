package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** backup code exact lookup과 상태 전이에 필요한 최소 row 모델입니다. */
public record BackupCodeRecord(
    long codeId,
    long userId,
    String codeHash,
    BackupCodeStatus codeStatus,
    Instant usedAt,
    Instant createdAt) {

  public BackupCodeRecord {
    if (codeId <= 0) {
      throw new IllegalArgumentException("codeId must be positive");
    }
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (codeHash == null || codeHash.isBlank()) {
      throw new IllegalArgumentException("codeHash is required");
    }
    if (codeStatus == null) {
      throw new IllegalArgumentException("codeStatus is required");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt is required");
    }
  }
}
