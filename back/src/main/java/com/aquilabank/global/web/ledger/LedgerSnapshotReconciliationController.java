package com.aquilabank.global.web.ledger;

import com.aquilabank.domain.ledger.model.LedgerSnapshotRecoveryCommand;
import com.aquilabank.domain.ledger.usecase.LedgerSnapshotDriftQueryUseCase;
import com.aquilabank.domain.ledger.usecase.LedgerSnapshotRecoveryUseCase;
import com.aquilabank.global.config.LedgerSnapshotReconciliationProperties;
import com.aquilabank.global.security.InternalServiceRequestAuthorizer;
import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenClaims;
import com.aquilabank.global.web.RequestTraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** reconciliation 결과 조회와 계좌 단건 recovery를 내부 ledger ops surface로 제한합니다. */
@Validated
@RestController
@RequestMapping("/internal/api/v1/ledger/snapshot-reconciliation")
@ConditionalOnProperty(name = "ledger.snapshot-reconciliation.enabled", havingValue = "true")
public class LedgerSnapshotReconciliationController {

  private final LedgerSnapshotDriftQueryUseCase driftQueryUseCase;
  private final LedgerSnapshotRecoveryUseCase recoveryUseCase;
  private final InternalServiceRequestAuthorizer internalServiceRequestAuthorizer;
  private final LedgerSnapshotReconciliationProperties properties;

  public LedgerSnapshotReconciliationController(
      LedgerSnapshotDriftQueryUseCase driftQueryUseCase,
      LedgerSnapshotRecoveryUseCase recoveryUseCase,
      InternalServiceRequestAuthorizer internalServiceRequestAuthorizer,
      LedgerSnapshotReconciliationProperties properties) {
    this.driftQueryUseCase = driftQueryUseCase;
    this.recoveryUseCase = recoveryUseCase;
    this.internalServiceRequestAuthorizer = internalServiceRequestAuthorizer;
    this.properties = properties;
  }

  @GetMapping("/drifts")
  public LedgerSnapshotDriftListResponse getOpenDrifts(
      HttpServletRequest request,
      @RequestParam(required = false, defaultValue = "0")
          @PositiveOrZero(message = "afterAccountId must not be negative") long afterAccountId,
      @RequestParam(required = false) @Positive(message = "limit must be positive") Integer limit) {
    internalServiceRequestAuthorizer.requireScope(request, InternalServiceScope.LEDGER_OPS);
    int resolvedLimit =
        limit == null ? properties.driftListLimit() : Math.min(limit, properties.driftListLimit());
    return LedgerSnapshotDriftListResponse.from(
        afterAccountId,
        resolvedLimit,
        driftQueryUseCase.findOpenDrifts(afterAccountId, resolvedLimit));
  }

  @PostMapping("/accounts/{accountId}/recovery")
  public LedgerSnapshotRecoveryResponse recoverSnapshot(
      HttpServletRequest request,
      @PathVariable @Positive(message = "accountId must be positive") long accountId,
      @Valid @RequestBody LedgerSnapshotRecoveryRequest body) {
    InternalServiceTokenClaims claims =
        internalServiceRequestAuthorizer.requireScope(request, InternalServiceScope.LEDGER_OPS);
    String reason = body.reason().trim();
    if (reason.length() > properties.recoveryReasonMaxLength()) {
      throw new IllegalArgumentException(
          "reason must be " + properties.recoveryReasonMaxLength() + " characters or less");
    }
    String requestId = RequestTraceContext.currentRequestId().orElse("unknown");
    return LedgerSnapshotRecoveryResponse.from(
        recoveryUseCase.recoverSnapshot(
            new LedgerSnapshotRecoveryCommand(accountId, reason, claims.subject(), requestId)));
  }
}
