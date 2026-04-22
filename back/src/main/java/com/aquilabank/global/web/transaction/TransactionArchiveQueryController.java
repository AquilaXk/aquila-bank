package com.aquilabank.global.web.transaction;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.domain.transaction.model.TransactionDirection;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import com.aquilabank.domain.transaction.usecase.TransactionArchiveQueryUseCase;
import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipal;
import com.aquilabank.global.web.security.RequestAccountAuthorizationService;
import jakarta.validation.constraints.Positive;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** archive transaction timeline read use case를 노출하는 HTTP adapter */
@Validated
@RestController
@RequestMapping("/api/v1/transactions/archive")
public class TransactionArchiveQueryController {

  private final TransactionArchiveQueryUseCase transactionArchiveQueryUseCase;
  private final RequestAccountAuthorizationService requestAccountAuthorizationService;

  public TransactionArchiveQueryController(
      TransactionArchiveQueryUseCase transactionArchiveQueryUseCase,
      RequestAccountAuthorizationService requestAccountAuthorizationService) {
    this.transactionArchiveQueryUseCase = transactionArchiveQueryUseCase;
    this.requestAccountAuthorizationService = requestAccountAuthorizationService;
  }

  @GetMapping
  public TransactionQueryResponse getArchivedTransactions(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @RequestParam @Positive(message = "accountId must be positive") long accountId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
      @RequestParam(defaultValue = "20") int limit,
      @RequestParam(required = false) String cursor,
      @RequestParam(required = false) TransactionStatus status,
      @RequestParam(required = false) TransactionDirection direction,
      @RequestParam(required = false) Long minAmountMinor,
      @RequestParam(required = false) Long maxAmountMinor,
      @RequestParam(required = false) String transactionReference) {
    try {
      TransactionCursor decodedCursor =
          cursor == null || cursor.isBlank() ? null : TransactionCursorCodec.decode(cursor);
      long resolvedAccountId =
          requestAccountAuthorizationService.resolveReadableAccountId(principal, accountId);
      TransactionQuery query =
          new TransactionQuery(
              resolvedAccountId,
              from.toInstant(),
              to.toInstant(),
              limit,
              decodedCursor,
              status,
              direction,
              minAmountMinor,
              maxAmountMinor,
              transactionReference);
      return TransactionQueryResponse.from(
          transactionArchiveQueryUseCase.getArchivedTransactions(query));
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }
  }
}
