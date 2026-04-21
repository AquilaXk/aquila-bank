package com.aquilabank.domain.ledger.port;

import java.time.Instant;

public interface TransferLimitUsageReadPort {

  long sumBookedDebitAmountMinor(
      long sourceAccountId, String currencyCode, Instant fromInclusive, Instant toExclusive);
}
