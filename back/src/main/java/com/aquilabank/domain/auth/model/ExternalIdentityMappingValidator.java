package com.aquilabank.domain.auth.model;

final class ExternalIdentityMappingValidator {

  private ExternalIdentityMappingValidator() {}

  static void validate(
      long userId,
      String providerId,
      String subject,
      AuthStatusChangeReason normalizedReason,
      String actorSubject,
      String requestId) {
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
    if (normalizedReason == null) {
      throw new IllegalArgumentException("reason is required");
    }
    if (actorSubject == null || actorSubject.isBlank()) {
      throw new IllegalArgumentException("actorSubject is required");
    }
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId is required");
    }
    if (requestId.length() > 64) {
      throw new IllegalArgumentException("requestId must be 64 characters or less");
    }
  }
}
