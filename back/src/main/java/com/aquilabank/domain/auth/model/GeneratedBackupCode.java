package com.aquilabank.domain.auth.model;

/** 발급 응답용 plain backup code와 저장용 hash를 함께 담습니다. */
public record GeneratedBackupCode(String plainCode, String codeHash) {

  public GeneratedBackupCode {
    if (plainCode == null || plainCode.isBlank()) {
      throw new IllegalArgumentException("plainCode is required");
    }
    if (codeHash == null || codeHash.isBlank()) {
      throw new IllegalArgumentException("codeHash is required");
    }
  }
}
