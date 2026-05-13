package com.aquilabank.global.web.ledger;

/** 공개 송금 preview 수취인 조회가 짧은 window 상한을 넘은 상태 */
public final class TransferRecipientPreviewThrottledException extends RuntimeException {

  private final long retryAfterSeconds;

  public TransferRecipientPreviewThrottledException(long retryAfterSeconds) {
    super("too many recipient preview requests");
    if (retryAfterSeconds <= 0) {
      throw new IllegalArgumentException("retryAfterSeconds must be positive");
    }
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
