package com.aquilabank.global.security;

/** 내부 bootstrap API token 검증 실패를 401로 돌리기 위한 예외 */
public final class BootstrapApiAccessDeniedException extends RuntimeException {

  public BootstrapApiAccessDeniedException(String message) {
    super(message);
  }
}
