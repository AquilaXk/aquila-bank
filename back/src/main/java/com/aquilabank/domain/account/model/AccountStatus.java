package com.aquilabank.domain.account.model;

/** 운영에서 계좌 lifecycle을 고정된 상태값으로만 바꾸게 제한합니다. */
public enum AccountStatus {
  ACTIVE,
  LOCKED,
  CLOSED
}
