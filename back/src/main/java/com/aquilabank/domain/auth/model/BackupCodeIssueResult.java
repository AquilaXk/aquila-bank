package com.aquilabank.domain.auth.model;

import java.util.List;

/** 발급 직후 1회 노출할 plain backup code 목록 응답입니다. */
public record BackupCodeIssueResult(List<String> backupCodes, int codeCount) {

  public BackupCodeIssueResult {
    if (backupCodes == null || backupCodes.isEmpty()) {
      throw new IllegalArgumentException("backupCodes must not be empty");
    }
    if (codeCount <= 0) {
      throw new IllegalArgumentException("codeCount must be positive");
    }
    if (backupCodes.size() != codeCount) {
      throw new IllegalArgumentException("backupCodes size must match codeCount");
    }
  }
}
