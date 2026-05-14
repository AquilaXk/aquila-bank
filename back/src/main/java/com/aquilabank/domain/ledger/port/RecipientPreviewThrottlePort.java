package com.aquilabank.domain.ledger.port;

import com.aquilabank.domain.ledger.model.RecipientPreviewThrottleDecision;
import java.time.Instant;

public interface RecipientPreviewThrottlePort {

  RecipientPreviewThrottleDecision consume(
      long userId, Instant now, int maxAttempts, long windowSeconds);
}
