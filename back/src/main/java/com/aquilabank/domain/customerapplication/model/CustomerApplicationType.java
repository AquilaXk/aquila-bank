package com.aquilabank.domain.customerapplication.model;

public enum CustomerApplicationType {
  BILL_PAYMENT,
  OPEN_BANKING_CONNECTION,
  DEPOSIT_PRODUCT_APPLICATION,
  LOAN_APPLICATION,
  FOREIGN_EXCHANGE_APPLICATION,
  CERTIFICATE_ISSUANCE,
  CERTIFICATE_REGISTRATION,
  SECURITY_MEDIA_APPLICATION,
  TRANSFER_LIMIT_CHANGE,
  INCIDENT_REPORT;

  public boolean requiresTotp() {
    return true;
  }
}
