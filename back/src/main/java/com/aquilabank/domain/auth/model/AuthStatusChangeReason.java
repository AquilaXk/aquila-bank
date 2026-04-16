package com.aquilabank.domain.auth.model;

/** 사유 코드는 분류용, detail은 운영 문맥 보존용으로 분리합니다. */
public record AuthStatusChangeReason(AuthStatusChangeReasonCode reasonCode, String reasonDetail) {

  public static final int MAX_REASON_DETAIL_LENGTH = 200;

  public AuthStatusChangeReason {
    if (reasonCode == null) {
      throw new IllegalArgumentException("reasonCode is required");
    }
    if (reasonDetail == null || reasonDetail.isBlank()) {
      throw new IllegalArgumentException("reasonDetail is required");
    }
    if (reasonDetail.length() > MAX_REASON_DETAIL_LENGTH) {
      throw new IllegalArgumentException("reasonDetail must be 200 characters or less");
    }
  }
}
