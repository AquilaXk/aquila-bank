package com.aquilabank.domain.customerapplication.model;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** 이체한도 변경 payload는 신청 접수와 실행 단계에서 같은 parser를 사용합니다. */
public record CustomerTransferLimitChangeRequest(
    long singleTransferLimitMinor,
    long dailyTransferLimitMinor,
    String changeReason,
    String evidenceReference) {

  private static final String SINGLE_LIMIT_KEY = "singleTransferLimitMinor";
  private static final String DAILY_LIMIT_KEY = "dailyTransferLimitMinor";
  private static final String REQUESTED_SINGLE_LIMIT_KEY = "requestedSingleTransferLimitMinor";
  private static final String REQUESTED_DAILY_LIMIT_KEY = "requestedDailyTransferLimitMinor";
  private static final Pattern EVIDENCE_REFERENCE = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{5,79}$");

  public CustomerTransferLimitChangeRequest {
    if (singleTransferLimitMinor <= 0) {
      throw new IllegalArgumentException("singleTransferLimitMinor must be positive");
    }
    if (dailyTransferLimitMinor < singleTransferLimitMinor) {
      throw new IllegalArgumentException(
          "dailyTransferLimitMinor must be greater than or equal to singleTransferLimitMinor");
    }
    changeReason = requiredText(changeReason, "changeReason", 120);
    evidenceReference = requiredText(evidenceReference, "evidenceReference", 80);
    if (!EVIDENCE_REFERENCE.matcher(evidenceReference).matches()) {
      throw new IllegalArgumentException("evidenceReference must be an uppercase reference code");
    }
  }

  public static Optional<CustomerTransferLimitChangeRequest> fromPayload(
      Map<String, Object> payload) {
    if (payload == null) {
      return Optional.empty();
    }
    Long singleLimit = longPayloadValue(payload, SINGLE_LIMIT_KEY, REQUESTED_SINGLE_LIMIT_KEY);
    Long dailyLimit = longPayloadValue(payload, DAILY_LIMIT_KEY, REQUESTED_DAILY_LIMIT_KEY);
    String changeReason = textPayloadValue(payload, "changeReason");
    String evidenceReference = textPayloadValue(payload, "evidenceReference");
    if (singleLimit == null
        || dailyLimit == null
        || dailyLimit < singleLimit
        || changeReason == null
        || evidenceReference == null) {
      return Optional.empty();
    }
    return Optional.of(
        new CustomerTransferLimitChangeRequest(
            singleLimit, dailyLimit, changeReason, evidenceReference));
  }

  private static Long longPayloadValue(
      Map<String, Object> payload, String key, String fallbackKey) {
    Object value = payload.containsKey(key) ? payload.get(key) : payload.get(fallbackKey);
    if (value instanceof Number number) {
      return number.longValue();
    }
    if (value instanceof String stringValue && !stringValue.isBlank()) {
      try {
        return Long.parseLong(stringValue);
      } catch (NumberFormatException ex) {
        return null;
      }
    }
    return null;
  }

  private static String textPayloadValue(Map<String, Object> payload, String key) {
    Object value = payload.get(key);
    if (value instanceof String text && !text.isBlank()) {
      return text;
    }
    return null;
  }

  private static String requiredText(String value, String key, int maxLength) {
    if (value == null || value.isBlank() || value.length() > maxLength) {
      throw new IllegalArgumentException(key + " is required");
    }
    return value;
  }
}
