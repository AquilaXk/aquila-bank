package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 검증 성공한 backup code를 즉시 소진 처리할 때 씁니다. */
public record BackupCodeUseCommand(long codeId, Instant usedAt) {

  public BackupCodeUseCommand {
    if (codeId <= 0) {
      throw new IllegalArgumentException("codeId must be positive");
    }
    if (usedAt == null) {
      throw new IllegalArgumentException("usedAt is required");
    }
  }
}
