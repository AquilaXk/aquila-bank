package com.aquilabank.domain.customerapplication.model;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CustomerApplicationPayloadSchemaTest {

  @Test
  void acceptsMinimumPayloadForTypedApplications() {
    validPayloads()
        .forEach(
            (type, payload) ->
                assertDoesNotThrow(() -> CustomerApplicationPayloadSchema.validate(type, payload)));
  }

  @Test
  void rejectsMissingRequiredPayloadFields() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CustomerApplicationPayloadSchema.validate(
                CustomerApplicationType.SECURITY_MEDIA_APPLICATION, Map.of("mediaType", "OTP")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CustomerApplicationPayloadSchema.validate(CustomerApplicationType.BILL_PAYMENT, null));
  }

  @Test
  void rejectsInvalidAmountAndCurrencyValues() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CustomerApplicationPayloadSchema.validate(
                CustomerApplicationType.BILL_PAYMENT,
                Map.of(
                    "billerCode",
                    "GIRO",
                    "paymentNumber",
                    "1234567890",
                    "amountMinor",
                    0L,
                    "currencyCode",
                    "krw")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CustomerApplicationPayloadSchema.validate(
                CustomerApplicationType.BILL_PAYMENT,
                Map.of(
                    "billerCode",
                    "GIRO",
                    "paymentNumber",
                    "1234567890",
                    "amountMinor",
                    50_000L,
                    "currencyCode",
                    "krw")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CustomerApplicationPayloadSchema.validate(
                CustomerApplicationType.DEPOSIT_PRODUCT_APPLICATION,
                Map.of("productCode", "DEP-001", "amountMinor", "10_000", "currencyCode", "KRW")));
  }

  @Test
  void acceptsStringAmountAndRejectsSameFxCurrency() {
    assertDoesNotThrow(
        () ->
            CustomerApplicationPayloadSchema.validate(
                CustomerApplicationType.DEPOSIT_PRODUCT_APPLICATION,
                Map.of("productCode", "DEP-001", "amountMinor", "1000", "currencyCode", "KRW")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CustomerApplicationPayloadSchema.validate(
                CustomerApplicationType.FOREIGN_EXCHANGE_APPLICATION,
                Map.of(
                    "sourceCurrencyCode",
                    "KRW",
                    "targetCurrencyCode",
                    "KRW",
                    "amountMinor",
                    300_000L)));
  }

  @Test
  void rejectsUnsupportedReferenceLikeCodesAndEnums() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CustomerApplicationPayloadSchema.validate(
                CustomerApplicationType.OPEN_BANKING_CONNECTION,
                Map.of(
                    "institutionCode",
                    "bank-088",
                    "externalAccountNumber",
                    "1234567890",
                    "consentId",
                    "CONSENT-001")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CustomerApplicationPayloadSchema.validate(
                CustomerApplicationType.SECURITY_MEDIA_APPLICATION,
                Map.of("mediaType", "UNKNOWN", "deliveryMethod", "BRANCH")));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            CustomerApplicationPayloadSchema.validate(
                CustomerApplicationType.INCIDENT_REPORT,
                Map.of("incidentType", "OTHER", "description", "lost card")));
  }

  private static Map<CustomerApplicationType, Map<String, Object>> validPayloads() {
    return Map.ofEntries(
        Map.entry(
            CustomerApplicationType.BILL_PAYMENT,
            Map.of(
                "billerCode",
                "GIRO",
                "paymentNumber",
                "1234567890",
                "amountMinor",
                50_000L,
                "currencyCode",
                "KRW")),
        Map.entry(
            CustomerApplicationType.OPEN_BANKING_CONNECTION,
            Map.of(
                "institutionCode",
                "088",
                "externalAccountNumber",
                "1234567890",
                "consentId",
                "CONSENT-001")),
        Map.entry(
            CustomerApplicationType.DEPOSIT_PRODUCT_APPLICATION,
            Map.of("productCode", "DEP-001", "amountMinor", 1_000_000L, "currencyCode", "KRW")),
        Map.entry(
            CustomerApplicationType.LOAN_APPLICATION,
            Map.of(
                "productCode",
                "LOAN-001",
                "requestedAmountMinor",
                10_000_000L,
                "currencyCode",
                "KRW",
                "purpose",
                "HOUSING")),
        Map.entry(
            CustomerApplicationType.FOREIGN_EXCHANGE_APPLICATION,
            Map.of(
                "sourceCurrencyCode", "KRW", "targetCurrencyCode", "USD", "amountMinor", 300_000L)),
        Map.entry(
            CustomerApplicationType.CERTIFICATE_ISSUANCE,
            Map.of("certificateType", "BANKING", "subjectDn", "CN=alice")),
        Map.entry(
            CustomerApplicationType.CERTIFICATE_REGISTRATION,
            Map.of("certificateSerialNumber", "SERIAL-001", "issuerDn", "CN=issuer")),
        Map.entry(
            CustomerApplicationType.SECURITY_MEDIA_APPLICATION,
            Map.of("mediaType", "OTP", "deliveryMethod", "BRANCH")),
        Map.entry(
            CustomerApplicationType.INCIDENT_REPORT,
            Map.of("incidentType", "CARD_LOSS", "description", "lost card")));
  }
}
