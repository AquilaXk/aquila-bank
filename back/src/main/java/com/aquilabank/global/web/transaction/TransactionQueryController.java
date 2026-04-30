package com.aquilabank.global.web.transaction;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.domain.transaction.model.TransactionDirection;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import com.aquilabank.domain.transaction.usecase.TransactionQueryUseCase;
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

/** transaction timeline read use case를 노출하는 HTTP adapter */
@Validated
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionQueryController {

  private final TransactionQueryUseCase transactionQueryUseCase;
  private final RequestAccountAuthorizationService requestAccountAuthorizationService;
  private final TransactionReadHotPathMetrics hotPathMetrics;

  public TransactionQueryController(
      TransactionQueryUseCase transactionQueryUseCase,
      RequestAccountAuthorizationService requestAccountAuthorizationService,
      TransactionReadHotPathMetrics hotPathMetrics) {
    this.transactionQueryUseCase = transactionQueryUseCase;
    this.requestAccountAuthorizationService = requestAccountAuthorizationService;
    this.hotPathMetrics = hotPathMetrics;
  }

  @GetMapping
  public TransactionQueryResponse getTransactions(
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
    return hotPathMetrics.record(
        TransactionReadHotPathMetrics.ENDPOINT_ACTIVE,
        TransactionReadHotPathMetrics.STAGE_TOTAL,
        () ->
            getTransactionsMeasured(
                principal,
                accountId,
                from,
                to,
                limit,
                cursor,
                status,
                direction,
                minAmountMinor,
                maxAmountMinor,
                transactionReference));
  }

  private TransactionQueryResponse getTransactionsMeasured(
      AuthenticatedRequestPrincipal principal,
      long accountId,
      OffsetDateTime from,
      OffsetDateTime to,
      int limit,
      String cursor,
      TransactionStatus status,
      TransactionDirection direction,
      Long minAmountMinor,
      Long maxAmountMinor,
      String transactionReference) {
    try {
      // 첫 page와 후속 page를 같은 endpoint로 통일
      TransactionCursor decodedCursor =
          cursor == null || cursor.isBlank() ? null : TransactionCursorCodec.decode(cursor);
      long resolvedAccountId =
          hotPathMetrics.record(
              TransactionReadHotPathMetrics.ENDPOINT_ACTIVE,
              TransactionReadHotPathMetrics.STAGE_AUTHORIZATION,
              () ->
                  requestAccountAuthorizationService.resolveReadableAccountId(
                      principal, accountId));
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
      TransactionSlice slice =
          hotPathMetrics.record(
              TransactionReadHotPathMetrics.ENDPOINT_ACTIVE,
              TransactionReadHotPathMetrics.STAGE_USECASE,
              () -> transactionQueryUseCase.getTransactions(query));
      return hotPathMetrics.record(
          TransactionReadHotPathMetrics.ENDPOINT_ACTIVE,
          TransactionReadHotPathMetrics.STAGE_RESPONSE_MAPPING,
          () -> TransactionQueryResponse.from(slice));
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }
  }
}
