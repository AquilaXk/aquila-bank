package com.aquilabank.domain.customerapplication.model;

public enum CustomerApplicationType {
  BILL_PAYMENT(CustomerApplicationProcessingMode.EXTERNAL_PROVIDER_REQUIRED),
  OPEN_BANKING_CONNECTION(CustomerApplicationProcessingMode.EXTERNAL_PROVIDER_REQUIRED),
  DEPOSIT_PRODUCT_APPLICATION(CustomerApplicationProcessingMode.EXTERNAL_PROVIDER_REQUIRED),
  LOAN_APPLICATION(CustomerApplicationProcessingMode.EXTERNAL_PROVIDER_REQUIRED),
  FOREIGN_EXCHANGE_APPLICATION(CustomerApplicationProcessingMode.EXTERNAL_PROVIDER_REQUIRED),
  CERTIFICATE_ISSUANCE(CustomerApplicationProcessingMode.EXTERNAL_PROVIDER_REQUIRED),
  CERTIFICATE_REGISTRATION(CustomerApplicationProcessingMode.EXTERNAL_PROVIDER_REQUIRED),
  SECURITY_MEDIA_APPLICATION(CustomerApplicationProcessingMode.EXTERNAL_PROVIDER_REQUIRED),
  TRANSFER_LIMIT_CHANGE(CustomerApplicationProcessingMode.INTERNAL_EXECUTION),
  INCIDENT_REPORT(CustomerApplicationProcessingMode.MANUAL_REVIEW_REQUIRED);

  private final CustomerApplicationProcessingMode processingMode;

  CustomerApplicationType(CustomerApplicationProcessingMode processingMode) {
    this.processingMode = processingMode;
  }

  public CustomerApplicationProcessingMode processingMode() {
    return processingMode;
  }

  public boolean supportsAutomatedExecution() {
    return processingMode == CustomerApplicationProcessingMode.INTERNAL_EXECUTION;
  }

  public boolean requiresTotp() {
    return true;
  }
}
