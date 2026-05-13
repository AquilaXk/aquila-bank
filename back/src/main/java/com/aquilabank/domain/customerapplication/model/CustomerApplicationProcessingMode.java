package com.aquilabank.domain.customerapplication.model;

/** 신청 타입별 처리 책임: 자동 실행과 외부/수동 처리 경계를 분리합니다. */
public enum CustomerApplicationProcessingMode {
  INTERNAL_EXECUTION,
  EXTERNAL_PROVIDER_REQUIRED,
  MANUAL_REVIEW_REQUIRED
}
