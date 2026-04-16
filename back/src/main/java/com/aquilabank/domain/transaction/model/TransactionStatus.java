package com.aquilabank.domain.transaction.model;

/** Read-model status exposed to the timeline API. */
public enum TransactionStatus {
  PENDING,
  BOOKED,
  REVERSED
}
