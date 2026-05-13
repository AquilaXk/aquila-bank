package com.aquilabank.domain.customerapplication.model;

import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** 신청 타입별 최소 payload 계약입니다. 외부 실행 전 단계에서도 잘못된 신청 접수를 줄입니다. */
public final class CustomerApplicationPayloadSchema {

  private static final Pattern CURRENCY_CODE = Pattern.compile("^[A-Z]{3}$");
  private static final Pattern POSITIVE_INTEGER = Pattern.compile("^[1-9][0-9]*$");

  private CustomerApplicationPayloadSchema() {}

  public static void validate(CustomerApplicationType type, Map<String, Object> payload) {
    Objects.requireNonNull(type, "applicationType is required");
    if (payload == null) {
      throw new IllegalArgumentException("payload is required");
    }
    switch (type) {
      case BILL_PAYMENT -> validateBillPayment(payload);
      case OPEN_BANKING_CONNECTION -> validateOpenBankingConnection(payload);
      case DEPOSIT_PRODUCT_APPLICATION -> validateDepositProductApplication(payload);
      case LOAN_APPLICATION -> validateLoanApplication(payload);
      case FOREIGN_EXCHANGE_APPLICATION -> validateForeignExchangeApplication(payload);
      case CERTIFICATE_ISSUANCE -> validateCertificateIssuance(payload);
      case CERTIFICATE_REGISTRATION -> validateCertificateRegistration(payload);
      case SECURITY_MEDIA_APPLICATION -> validateSecurityMediaApplication(payload);
      case INCIDENT_REPORT -> validateIncidentReport(payload);
      case TRANSFER_LIMIT_CHANGE -> {
        // 한도 변경은 금액 상한 정책까지 포함한 전용 parser가 use case에서 검증합니다.
      }
    }
  }

  private static void validateBillPayment(Map<String, Object> payload) {
    requireText(payload, "billerCode", 40);
    requireText(payload, "paymentNumber", 80);
    requirePositiveMinor(payload, "amountMinor");
    requireCurrencyCode(payload, "currencyCode");
  }

  private static void validateOpenBankingConnection(Map<String, Object> payload) {
    requireText(payload, "institutionCode", 20);
    requireText(payload, "externalAccountNumber", 40);
    requireText(payload, "consentId", 80);
  }

  private static void validateDepositProductApplication(Map<String, Object> payload) {
    requireText(payload, "productCode", 40);
    requirePositiveMinor(payload, "amountMinor");
    requireCurrencyCode(payload, "currencyCode");
  }

  private static void validateLoanApplication(Map<String, Object> payload) {
    requireText(payload, "productCode", 40);
    requirePositiveMinor(payload, "requestedAmountMinor");
    requireCurrencyCode(payload, "currencyCode");
    requireText(payload, "purpose", 120);
  }

  private static void validateForeignExchangeApplication(Map<String, Object> payload) {
    String sourceCurrencyCode = requireCurrencyCode(payload, "sourceCurrencyCode");
    String targetCurrencyCode = requireCurrencyCode(payload, "targetCurrencyCode");
    if (sourceCurrencyCode.equals(targetCurrencyCode)) {
      throw new IllegalArgumentException("targetCurrencyCode must differ from sourceCurrencyCode");
    }
    requirePositiveMinor(payload, "amountMinor");
  }

  private static void validateCertificateIssuance(Map<String, Object> payload) {
    requireText(payload, "certificateType", 40);
    requireText(payload, "subjectDn", 200);
  }

  private static void validateCertificateRegistration(Map<String, Object> payload) {
    requireText(payload, "certificateSerialNumber", 80);
    requireText(payload, "issuerDn", 200);
  }

  private static void validateSecurityMediaApplication(Map<String, Object> payload) {
    requireText(payload, "mediaType", 40);
    requireText(payload, "deliveryMethod", 40);
  }

  private static void validateIncidentReport(Map<String, Object> payload) {
    requireText(payload, "incidentType", 40);
    requireText(payload, "description", 500);
  }

  private static String requireCurrencyCode(Map<String, Object> payload, String key) {
    String value = requireText(payload, key, 3);
    if (!CURRENCY_CODE.matcher(value).matches()) {
      throw new IllegalArgumentException(key + " must be a 3-letter uppercase code");
    }
    return value;
  }

  private static long requirePositiveMinor(Map<String, Object> payload, String key) {
    Object value = payload.get(key);
    if (value instanceof Number number) {
      long result = number.longValue();
      if (result <= 0 || Double.compare(number.doubleValue(), (double) result) != 0) {
        throw new IllegalArgumentException(key + " must be a positive integer minor amount");
      }
      return result;
    }
    if (value instanceof String text && POSITIVE_INTEGER.matcher(text).matches()) {
      return Long.parseLong(text);
    }
    throw new IllegalArgumentException(key + " must be a positive integer minor amount");
  }

  private static String requireText(Map<String, Object> payload, String key, int maxLength) {
    Object value = payload.get(key);
    if (!(value instanceof String text) || text.isBlank() || text.length() > maxLength) {
      throw new IllegalArgumentException(key + " is required");
    }
    return text;
  }
}
