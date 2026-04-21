package com.aquilabank.domain.auth.model;

/** 외부 IdP 인증 결과를 내부 session 발급 입력으로 넘기는 command입니다. */
public record ExternalOidcLoginCommand(
    String providerId, String subject, AuthSessionClientMetadata sessionClientMetadata) {

  public ExternalOidcLoginCommand {
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
    if (sessionClientMetadata == null) {
      throw new IllegalArgumentException("sessionClientMetadata is required");
    }
  }
}
