package com.aquilabank.domain.transaction.model;

/** 거래 조회용 상태 */
public enum TransactionStatus {
  PENDING,
  BOOKED,
  PARTIALLY_REVERSED,
  REVERSED
}
