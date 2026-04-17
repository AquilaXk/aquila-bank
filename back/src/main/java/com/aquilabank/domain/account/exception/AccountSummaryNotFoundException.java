package com.aquilabank.domain.account.exception;

/** 계좌 요약 조회 결과 부재 예외 */
public class AccountSummaryNotFoundException extends RuntimeException {

  public AccountSummaryNotFoundException(String message) {
    super(message);
  }
}
