package com.aquilabank.domain.ledger.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.ledger.exception.LedgerAuditEntryNotFoundException;
import com.aquilabank.domain.ledger.model.LedgerAuditEntry;
import com.aquilabank.domain.ledger.port.LedgerAuditLookupPort;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class LedgerAuditLookupServiceTest {

  private static final LedgerAuditEntry ENTRY =
      new LedgerAuditEntry(
          10L,
          101L,
          "TRX-audit-001",
          "ENT-audit-001",
          "DEBIT",
          "BOOKED",
          1_000L,
          "KRW",
          Instant.parse("2026-04-21T00:00:00Z"),
          Instant.parse("2026-04-21T00:00:00Z"),
          "audit transfer",
          "audit-request-001",
          Instant.parse("2026-04-21T00:00:00Z"));

  private final LedgerAuditLookupPort lookupPort = Mockito.mock(LedgerAuditLookupPort.class);
  private final LedgerAuditLookupService service = new LedgerAuditLookupService(lookupPort);

  @Test
  void delegatesRequestIdLookupWithBoundedCursorArguments() {
    when(lookupPort.findByRequestId("audit-request-001", 5L, 20)).thenReturn(List.of(ENTRY));

    List<LedgerAuditEntry> result = service.findByRequestId("audit-request-001", 5L, 20);

    assertThat(result).containsExactly(ENTRY);
    verify(lookupPort).findByRequestId("audit-request-001", 5L, 20);
  }

  @Test
  void delegatesTransactionReferenceLookupWithBoundedCursorArguments() {
    when(lookupPort.findByTransactionReference("TRX-audit-001", 0L, 10)).thenReturn(List.of(ENTRY));

    List<LedgerAuditEntry> result = service.findByTransactionReference("TRX-audit-001", 0L, 10);

    assertThat(result).containsExactly(ENTRY);
    verify(lookupPort).findByTransactionReference("TRX-audit-001", 0L, 10);
  }

  @Test
  void returnsEntryReferenceLookupResult() {
    when(lookupPort.findByEntryReference("ENT-audit-001")).thenReturn(Optional.of(ENTRY));

    LedgerAuditEntry result = service.getByEntryReference("ENT-audit-001");

    assertThat(result).isEqualTo(ENTRY);
    verify(lookupPort).findByEntryReference("ENT-audit-001");
  }

  @Test
  void rejectsInvalidLookupArguments() {
    assertThatThrownBy(() -> service.findByRequestId(" ", 0L, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("requestId is required");
    assertThatThrownBy(() -> service.findByRequestId("audit-request-001", -1L, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("afterEntryId must not be negative");
    assertThatThrownBy(() -> service.findByRequestId("audit-request-001", 0L, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("limit must be positive");
  }

  @Test
  void throwsWhenEntryReferenceDoesNotExist() {
    when(lookupPort.findByEntryReference("ENT-missing")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getByEntryReference("ENT-missing"))
        .isInstanceOf(LedgerAuditEntryNotFoundException.class)
        .hasMessage("ledger audit entry is not found");
  }
}
