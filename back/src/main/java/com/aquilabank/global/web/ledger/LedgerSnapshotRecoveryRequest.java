package com.aquilabank.global.web.ledger;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LedgerSnapshotRecoveryRequest(
    @NotBlank(message = "reason is required") @Size(max = 200, message = "reason must be 200 characters or less") String reason) {}
