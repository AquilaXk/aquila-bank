package com.aquilabank.domain.auth.usecase;

/** 고위험 업무 실행 전 TOTP 재검증이 필요한지 판단하는 진입점입니다. */
public interface TotpOperationRequirementUseCase {

  boolean requiresVerification(long userId);
}
