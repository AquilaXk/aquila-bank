package com.aquilabank.domain.auth.model;

/** legacy reason 입력을 한시 호환하면서 내부 command는 단일 reason 구조로 정규화합니다. */
public final class AuthStatusChangeReasonNormalizer {

  private AuthStatusChangeReasonNormalizer() {}

  public static AuthStatusChangeReason normalize(
      AuthStatusChangeReasonCode reasonCode, String reasonDetail, String legacyReason) {
    boolean hasLegacyReason = hasText(legacyReason);
    boolean hasReasonDetail = hasText(reasonDetail);

    if (hasLegacyReason && (reasonCode != null || hasReasonDetail)) {
      throw new IllegalArgumentException(
          "reason and reasonCode/reasonDetail cannot be used together");
    }
    if (hasLegacyReason) {
      return new AuthStatusChangeReason(AuthStatusChangeReasonCode.LEGACY_FREE_TEXT, legacyReason);
    }
    if (reasonCode == null) {
      throw new IllegalArgumentException("reasonCode is required");
    }
    return new AuthStatusChangeReason(reasonCode, reasonDetail);
  }

  private static boolean hasText(String value) {
    return value != null && !value.isBlank();
  }
}
