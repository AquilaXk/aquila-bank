package com.aquilabank.domain.account.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.account.exception.AccountSummaryNotFoundException;
import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.port.AccountSummaryReadPort;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AccountSummaryQueryServiceTest {

  private AccountSummaryReadPort accountSummaryReadPort;
  private AccountSummaryQueryService service;

  @BeforeEach
  void setUp() {
    accountSummaryReadPort = mock(AccountSummaryReadPort.class);
    service = new AccountSummaryQueryService(accountSummaryReadPort);
  }

  @Test
  void returnsAccountSummaryByAccountNumberExactLookup() {
    AccountSummary summary = account(202L, "999900001234");
    when(accountSummaryReadPort.findByAccountNumber("999900001234"))
        .thenReturn(Optional.of(summary));

    AccountSummary result = service.getByAccountNumber("999900001234");

    assertThat(result).isSameAs(summary);
  }

  @Test
  void rejectsBlankAccountNumber() {
    assertThatThrownBy(() -> service.getByAccountNumber(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("accountNumber is required");
  }

  @Test
  void throwsNotFoundWhenAccountNumberDoesNotExist() {
    when(accountSummaryReadPort.findByAccountNumber("999900009999")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.getByAccountNumber("999900009999"))
        .isInstanceOf(AccountSummaryNotFoundException.class)
        .hasMessage("account summary is not found");
  }

  private static AccountSummary account(long accountId, String accountNumber) {
    return new AccountSummary(
        accountId,
        accountNumber,
        "홍길동",
        "ACTIVE",
        "KRW",
        0L,
        0L,
        Instant.parse("2026-05-11T00:00:00Z"),
        Instant.parse("2026-05-11T00:00:00Z"));
  }
}
