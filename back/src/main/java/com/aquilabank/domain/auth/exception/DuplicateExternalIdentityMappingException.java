package com.aquilabank.domain.auth.exception;

/** 같은 provider subject 또는 provider user 매핑 중복을 막습니다. */
public class DuplicateExternalIdentityMappingException extends RuntimeException {

  public DuplicateExternalIdentityMappingException(String message) {
    super(message);
  }
}
