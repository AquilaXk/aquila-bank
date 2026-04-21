package com.aquilabank.domain.auth.model;

import java.time.Instant;

/** 외부 provider subject와 내부 user 연결 결과입니다. */
public record ExternalIdentityMapping(
    long userId, String providerId, String subject, Instant createdAt, Instant updatedAt) {

  public ExternalIdentityMapping {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (providerId == null || providerId.isBlank()) {
      throw new IllegalArgumentException("providerId is required");
    }
    if (providerId.length() > 64) {
      throw new IllegalArgumentException("providerId must be 64 characters or less");
    }
    if (subject == null || subject.isBlank()) {
      throw new IllegalArgumentException("subject is required");
    }
    if (subject.length() > 255) {
      throw new IllegalArgumentException("subject must be 255 characters or less");
    }
    if (createdAt == null) {
      throw new IllegalArgumentException("createdAt is required");
    }
    if (updatedAt == null) {
      throw new IllegalArgumentException("updatedAt is required");
    }
  }
}
