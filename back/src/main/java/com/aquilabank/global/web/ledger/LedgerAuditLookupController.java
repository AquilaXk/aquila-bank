package com.aquilabank.global.web.ledger;

import com.aquilabank.domain.ledger.usecase.LedgerAuditLookupUseCase;
import com.aquilabank.global.security.InternalServiceRequestAuthorizer;
import com.aquilabank.global.security.InternalServiceScope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ledger 원본 추적 API는 내부 운영 token과 bounded cursor 조회로만 허용합니다. */
@Validated
@RestController
@RequestMapping("/internal/api/v1/ledger/audit")
public class LedgerAuditLookupController {

  private static final int DEFAULT_LIMIT = 50;
  private static final int MAX_LIMIT = 100;

  private final LedgerAuditLookupUseCase lookupUseCase;
  private final InternalServiceRequestAuthorizer internalServiceRequestAuthorizer;

  public LedgerAuditLookupController(
      LedgerAuditLookupUseCase lookupUseCase,
      InternalServiceRequestAuthorizer internalServiceRequestAuthorizer) {
    this.lookupUseCase = lookupUseCase;
    this.internalServiceRequestAuthorizer = internalServiceRequestAuthorizer;
  }

  @GetMapping("/request-ids/{requestId}/entries")
  public LedgerAuditEntryListResponse findByRequestId(
      HttpServletRequest request,
      @PathVariable String requestId,
      @RequestParam(required = false, defaultValue = "0")
          @PositiveOrZero(message = "afterEntryId must not be negative") long afterEntryId,
      @RequestParam(required = false) @Positive(message = "limit must be positive") Integer limit) {
    internalServiceRequestAuthorizer.requireScope(request, InternalServiceScope.LEDGER_OPS);
    int resolvedLimit = resolveLimit(limit);
    return LedgerAuditEntryListResponse.from(
        "REQUEST_ID",
        requestId,
        afterEntryId,
        resolvedLimit,
        lookupUseCase.findByRequestId(requestId, afterEntryId, resolvedLimit));
  }

  @GetMapping("/transactions/{transactionReference}/entries")
  public LedgerAuditEntryListResponse findByTransactionReference(
      HttpServletRequest request,
      @PathVariable String transactionReference,
      @RequestParam(required = false, defaultValue = "0")
          @PositiveOrZero(message = "afterEntryId must not be negative") long afterEntryId,
      @RequestParam(required = false) @Positive(message = "limit must be positive") Integer limit) {
    internalServiceRequestAuthorizer.requireScope(request, InternalServiceScope.LEDGER_OPS);
    int resolvedLimit = resolveLimit(limit);
    return LedgerAuditEntryListResponse.from(
        "TRANSACTION_REFERENCE",
        transactionReference,
        afterEntryId,
        resolvedLimit,
        lookupUseCase.findByTransactionReference(
            transactionReference, afterEntryId, resolvedLimit));
  }

  @GetMapping("/entries/{entryReference}")
  public LedgerAuditEntryResponse findByEntryReference(
      HttpServletRequest request, @PathVariable String entryReference) {
    internalServiceRequestAuthorizer.requireScope(request, InternalServiceScope.LEDGER_OPS);
    return LedgerAuditEntryResponse.from(lookupUseCase.getByEntryReference(entryReference));
  }

  private int resolveLimit(Integer limit) {
    return limit == null ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
  }
}
