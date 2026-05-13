package com.aquilabank.domain.customerapplication.model;

import java.time.Instant;

/** 한도 변경 신청 실행 결과를 실제 계좌별 송금 한도로 반영하는 command입니다. */
public record CustomerTransferLimitPolicyCommand(
    long userId,
    long accountId,
    long singleTransferLimitMinor,
    long dailyTransferLimitMinor,
    String applicationReference,
    String actorSubject,
    String requestId,
    Instant approvedAt) {

  public CustomerTransferLimitPolicyCommand {
    if (userId <= 0) {
      throw new IllegalArgumentException("userId must be positive");
    }
    if (accountId <= 0) {
      throw new IllegalArgumentException("accountId must be positive");
    }
    if (singleTransferLimitMinor <= 0) {
      throw new IllegalArgumentException("singleTransferLimitMinor must be positive");
    }
    if (dailyTransferLimitMinor < singleTransferLimitMinor) {
      throw new IllegalArgumentException(
          "dailyTransferLimitMinor must be greater than or equal to singleTransferLimitMinor");
    }
    if (applicationReference == null || applicationReference.isBlank()) {
      throw new IllegalArgumentException("applicationReference is required");
    }
    if (actorSubject == null || actorSubject.isBlank()) {
      throw new IllegalArgumentException("actorSubject is required");
    }
    if (requestId == null || requestId.isBlank()) {
      throw new IllegalArgumentException("requestId is required");
    }
    if (approvedAt == null) {
      throw new IllegalArgumentException("approvedAt is required");
    }
  }
}
