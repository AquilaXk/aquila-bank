package com.aquilabank.domain.auth.exception;

/** 삭제 대상 external identity 매핑이 없을 때 사용합니다. */
public class ExternalIdentityMappingNotFoundException extends RuntimeException {

  public ExternalIdentityMappingNotFoundException(String message) {
    super(message);
  }
}
