package com.aquilabank.domain.auth.model;

/** internal admin이 외부 identity를 기존 user에 연결할 때 쓰는 command입니다. */
public record ExternalIdentityLinkCommand(
    long userId,
    String providerId,
    String subject,
    AuthStatusChangeReason normalizedReason,
    String actorSubject,
    String requestId) {

  public ExternalIdentityLinkCommand {
    ExternalIdentityMappingValidator.validate(
        userId, providerId, subject, normalizedReason, actorSubject, requestId);
  }
}
