package com.aquilabank.global.web.transaction;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.domain.transaction.model.TransactionDirection;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import com.aquilabank.global.web.security.RequestAccountAuthorizationService;
import java.time.OffsetDateTime;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 활성/아카이브 거래 타임라인 조회 컨트롤러의 공통 실행 파이프라인을 캡슐화합니다. */
final class TransactionTimelineQuerySupport {

  private final String endpoint;
  private final RequestAccountAuthorizationService requestAccountAuthorizationService;
  private final TransactionReadHotPathMetrics hotPathMetrics;
  private final TransactionReadSingleFlight singleFlight;
  private final TransactionReadAccountFairnessLimiter accountFairnessLimiter;

  TransactionTimelineQuerySupport(
      String endpoint,
      RequestAccountAuthorizationService requestAccountAuthorizationService,
      TransactionReadHotPathMetrics hotPathMetrics,
      TransactionReadSingleFlight singleFlight,
      TransactionReadAccountFairnessLimiter accountFairnessLimiter) {
    this.endpoint = endpoint;
    this.requestAccountAuthorizationService = requestAccountAuthorizationService;
    this.hotPathMetrics = hotPathMetrics;
    this.singleFlight = singleFlight;
    this.accountFairnessLimiter = accountFairnessLimiter;
  }

  TransactionQueryResponse execute(
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
      String transactionReference,
      String responseShape,
      Function<TransactionQuery, TransactionSlice> queryExecutor) {
    return hotPathMetrics.record(
        endpoint,
        TransactionReadHotPathMetrics.STAGE_TOTAL,
        () ->
            executeMeasured(
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
                transactionReference,
                responseShape,
                queryExecutor));
  }

  private TransactionQueryResponse executeMeasured(
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
      String transactionReference,
      String responseShape,
      Function<TransactionQuery, TransactionSlice> queryExecutor) {
    try {
      TransactionCursor decodedCursor =
          cursor == null || cursor.isBlank() ? null : TransactionCursorCodec.decode(cursor);
      long resolvedAccountId =
          hotPathMetrics.record(
              endpoint,
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
      TransactionResponseShape resolvedResponseShape = TransactionResponseShape.from(responseShape);
      TransactionSlice slice =
          hotPathMetrics.record(
              endpoint,
              TransactionReadHotPathMetrics.STAGE_USECASE,
              () ->
                  accountFairnessLimiter.execute(
                      resolvedAccountId,
                      () -> singleFlight.get(endpoint, query, () -> queryExecutor.apply(query))));
      return hotPathMetrics.record(
          endpoint,
          TransactionReadHotPathMetrics.STAGE_RESPONSE_MAPPING,
          () -> TransactionQueryResponse.from(slice, resolvedResponseShape));
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }
  }
}
