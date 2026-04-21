package com.aquilabank.domain.auth.model;

/** internal admin이 외부 identity 연결을 해제할 때 쓰는 command입니다. */
public record ExternalIdentityUnlinkCommand(
    long userId,
    String providerId,
    String subject,
    AuthStatusChangeReason normalizedReason,
    String actorSubject,
    String requestId) {

  public ExternalIdentityUnlinkCommand {
    ExternalIdentityMappingValidator.validate(
        userId, providerId, subject, normalizedReason, actorSubject, requestId);
  }
}
