package com.aquilabank.domain.auth.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AuthStatusChangeReasonNormalizerTest {

  @Test
  void legacyReasonOnlyFallsBackToLegacyFreeTextCode() {
    AuthStatusChangeReason result =
        AuthStatusChangeReasonNormalizer.normalize(null, null, "legacy-free-text");

    assertThat(result.reasonCode()).isEqualTo(AuthStatusChangeReasonCode.LEGACY_FREE_TEXT);
    assertThat(result.reasonDetail()).isEqualTo("legacy-free-text");
  }

  @Test
  void rejectsLegacyReasonMixedWithReasonCode() {
    assertThatThrownBy(
            () ->
                AuthStatusChangeReasonNormalizer.normalize(
                    AuthStatusChangeReasonCode.OPS_MANUAL, null, "legacy-free-text"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("reason and reasonCode/reasonDetail cannot be used together");
  }

  @Test
  void rejectsLegacyReasonMixedWithReasonDetail() {
    assertThatThrownBy(
            () ->
                AuthStatusChangeReasonNormalizer.normalize(
                    null, "manual-revoke", "legacy-free-text"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("reason and reasonCode/reasonDetail cannot be used together");
  }
}
