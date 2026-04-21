package com.aquilabank.domain.auth.exception;

/** requestId 기준 external identity 감사 row가 없을 때 사용합니다. */
public class ExternalIdentityAuditNotFoundException extends RuntimeException {

  public ExternalIdentityAuditNotFoundException(String message) {
    super(message);
  }
}
