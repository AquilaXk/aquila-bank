package com.aquilabank.global.web.transaction;

/** transaction read 계좌별 공정성 상한 초과를 429 source로 분리합니다. */
public final class TransactionReadAccountFairnessRejectedException extends RuntimeException {

  public static final String SOURCE = "fairness-limiter";
  public static final String SCOPE = "transaction-read-account";

  public TransactionReadAccountFairnessRejectedException() {
    super("transaction read account concurrency limit exceeded");
  }
}
