package com.aquilabank.domain.ledger.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TransferReversalCommandTest {

  @Test
  void acceptsMissingAmountForFullRemainingReversal() {
    TransferReversalCommand command =
        new TransferReversalCommand(
            "TRX-1", 101L, null, TransferReversalReason.CANCEL, "cancel all", "rev-1");

    assertThat(command.amountMinor()).isNull();
  }

  @Test
  void rejectsNonPositivePartialAmount() {
    assertThatThrownBy(
            () ->
                new TransferReversalCommand(
                    "TRX-1", 101L, 0L, TransferReversalReason.CANCEL, "bad", "rev-1"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("amountMinor must be positive");
  }

  @Test
  void includesAmountInFingerprint() {
    TransferReversalCommand first =
        new TransferReversalCommand(
            "TRX-1", 101L, 500L, TransferReversalReason.CANCEL, "partial", "rev-1");
    TransferReversalCommand second =
        new TransferReversalCommand(
            "TRX-1", 101L, 700L, TransferReversalReason.CANCEL, "partial", "rev-1");

    assertThat(first.fingerprint()).isNotEqualTo(second.fingerprint());
    assertThat(first.fingerprint()).hasSize(64).matches("^[0-9a-f]{64}$");
  }

  @Test
  void produces64CharSha256HexFingerprintForMaxSummaryAndPreventsDelimiterCollision() {
    String maxSummary = "s".repeat(120);
    TransferCommand maxSummaryTransfer =
        new TransferCommand(101L, 202L, 1_000L, "KRW", maxSummary, "idem-max-summary");
    assertThat(maxSummaryTransfer.fingerprint()).hasSize(64).matches("^[0-9a-f]{64}$");

    TransferCommand pipeInSummaryA =
        new TransferCommand(101L, 202L, 1_000L, "KRW", "a|b", "idem-pipe-1");
    TransferCommand pipeInSummaryB =
        new TransferCommand(101L, 202L, 1_000L, "KRW", "a|c", "idem-pipe-1");
    assertThat(pipeInSummaryA.fingerprint()).isNotEqualTo(pipeInSummaryB.fingerprint());

    TransferReversalCommand reversalCollisionA =
        new TransferReversalCommand(
            "TRX-1|101", 202L, 500L, TransferReversalReason.CANCEL, "summary", "rev-pipe");
    TransferReversalCommand reversalCollisionB =
        new TransferReversalCommand(
            "TRX-1", 101L, 500L, TransferReversalReason.CANCEL, "202|summary", "rev-pipe");
    assertThat(reversalCollisionA.fingerprint()).isNotEqualTo(reversalCollisionB.fingerprint());
    assertThat(reversalCollisionA.fingerprint()).hasSize(64).matches("^[0-9a-f]{64}$");
  }
}
