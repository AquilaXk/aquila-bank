package com.aquilabank.domain.auth.exception;

public class AuthStatusChangeAuditNotFoundException extends RuntimeException {

  public AuthStatusChangeAuditNotFoundException(String message) {
    super(message);
  }
}
