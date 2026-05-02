package com.aquilabank.global.web.transaction;

/** transaction list response payload 크기 선택 */
enum TransactionResponseShape {
  FULL,
  SLIM;

  static TransactionResponseShape from(String value) {
    if (value == null || value.isBlank() || "full".equalsIgnoreCase(value)) {
      return FULL;
    }
    if ("slim".equalsIgnoreCase(value)) {
      return SLIM;
    }
    throw new IllegalArgumentException("responseShape must be full or slim");
  }
}
