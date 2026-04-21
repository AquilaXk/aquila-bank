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
  }
}
