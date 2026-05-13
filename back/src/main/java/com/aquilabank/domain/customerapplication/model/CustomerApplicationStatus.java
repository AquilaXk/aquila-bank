package com.aquilabank.domain.customerapplication.model;

public enum CustomerApplicationStatus {
  SUBMITTED,
  REVIEWING,
  APPROVED,
  PENDING_EXTERNAL,
  EXECUTED,
  FAILED,
  REJECTED,
  CANCELLED,
  // 기존 row 호환용 legacy 상태입니다. 신규 상태전이는 REVIEWING/EXECUTED를 사용합니다.
  IN_REVIEW,
  COMPLETED;

  public boolean isTerminal() {
    return switch (this) {
      case EXECUTED, FAILED, REJECTED, CANCELLED, COMPLETED -> true;
      default -> false;
    };
  }

  public boolean isReviewingState() {
    return this == REVIEWING || this == IN_REVIEW;
  }
}
