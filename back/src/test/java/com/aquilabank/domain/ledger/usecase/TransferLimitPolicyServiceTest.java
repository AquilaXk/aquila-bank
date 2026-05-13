package com.aquilabank.domain.ledger.usecase;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.ledger.exception.TransferLimitExceededException;
import com.aquilabank.domain.ledger.model.TransferCommand;
import com.aquilabank.domain.ledger.model.TransferLimitPolicy;
import com.aquilabank.domain.ledger.port.TransferLimitPolicyOverrideReadPort;
import com.aquilabank.domain.ledger.port.TransferLimitUsageReadPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TransferLimitPolicyServiceTest {

  private static final Instant NOW = Instant.parse("2026-04-21T01:00:00Z");
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final TransferLimitUsageReadPort usageReadPort =
      Mockito.mock(TransferLimitUsageReadPort.class);
  private final TransferLimitPolicyOverrideReadPort overrideReadPort =
      Mockito.mock(TransferLimitPolicyOverrideReadPort.class);

  @Test
  void rejectsWhenSingleTransferLimitIsExceededWithoutUsageLookup() {
    TransferLimitPolicyService service =
        new TransferLimitPolicyService(
            usageReadPort,
            overrideReadPort,
            Clock.fixed(NOW, ZoneOffset.UTC),
            SEOUL,
            new TransferLimitPolicy(1_000L, 10_000L));

    assertThatThrownBy(() -> service.validate(command(1_001L)))
        .isInstanceOf(TransferLimitExceededException.class)
        .hasMessage("single transfer limit exceeded");
    verifyNoInteractions(usageReadPort);
  }

  @Test
  void rejectsWhenDailyOutgoingLimitWouldBeExceededForSourceAccountDay() {
    TransferLimitPolicyService service =
        new TransferLimitPolicyService(
            usageReadPort,
            overrideReadPort,
            Clock.fixed(NOW, ZoneOffset.UTC),
            SEOUL,
            new TransferLimitPolicy(1_000L, 5_000L));
    Instant expectedFrom = Instant.parse("2026-04-20T15:00:00Z");
    Instant expectedTo = Instant.parse("2026-04-21T15:00:00Z");
    when(usageReadPort.sumBookedDebitAmountMinor(101L, "KRW", expectedFrom, expectedTo))
        .thenReturn(4_500L);

    assertThatThrownBy(() -> service.validate(command(600L)))
        .isInstanceOf(TransferLimitExceededException.class)
        .hasMessage("daily transfer limit exceeded");
    verify(usageReadPort).sumBookedDebitAmountMinor(101L, "KRW", expectedFrom, expectedTo);
  }

  @Test
  void allowsWhenSingleAndDailyLimitsAreNotExceeded() {
    TransferLimitPolicyService service =
        new TransferLimitPolicyService(
            usageReadPort,
            overrideReadPort,
            Clock.fixed(NOW, ZoneOffset.UTC),
            SEOUL,
            new TransferLimitPolicy(1_000L, 5_000L));
    Instant expectedFrom = Instant.parse("2026-04-20T15:00:00Z");
    Instant expectedTo = Instant.parse("2026-04-21T15:00:00Z");
    when(usageReadPort.sumBookedDebitAmountMinor(101L, "KRW", expectedFrom, expectedTo))
        .thenReturn(4_400L);

    assertThatCode(() -> service.validate(command(600L))).doesNotThrowAnyException();
    verify(usageReadPort).sumBookedDebitAmountMinor(101L, "KRW", expectedFrom, expectedTo);
  }

  @Test
  void usesAccountOverridePolicyBeforeDefaultPolicy() {
    TransferLimitPolicyService service =
        new TransferLimitPolicyService(
            usageReadPort,
            overrideReadPort,
            Clock.fixed(NOW, ZoneOffset.UTC),
            SEOUL,
            new TransferLimitPolicy(1_000L, 5_000L));
    Instant expectedFrom = Instant.parse("2026-04-20T15:00:00Z");
    Instant expectedTo = Instant.parse("2026-04-21T15:00:00Z");
    when(overrideReadPort.findByAccountId(101L))
        .thenReturn(Optional.of(new TransferLimitPolicy(5_000L, 10_000L)));
    when(usageReadPort.sumBookedDebitAmountMinor(101L, "KRW", expectedFrom, expectedTo))
        .thenReturn(4_000L);

    assertThatCode(() -> service.validate(command(3_000L))).doesNotThrowAnyException();
    verify(overrideReadPort).findByAccountId(101L);
    verify(usageReadPort).sumBookedDebitAmountMinor(101L, "KRW", expectedFrom, expectedTo);
  }

  private TransferCommand command(long amountMinor) {
    return new TransferCommand(
        101L, 202L, amountMinor, "KRW", "policy test", "transfer-limit-test");
  }
}
