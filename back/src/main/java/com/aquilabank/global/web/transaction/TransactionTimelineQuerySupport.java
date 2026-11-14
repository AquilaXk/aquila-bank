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
  private final int endpointIndex;
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
    this.endpointIndex = TransactionReadHotPathMetrics.endpointIndex(endpoint);
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
    long totalStart = hotPathMetrics.startTimestamp();
    boolean totalOk = false;
    try {
      long authStart = hotPathMetrics.startTimestamp();
      boolean authOk = false;
      long resolvedAccountId;
      try {
        resolvedAccountId =
            requestAccountAuthorizationService.resolveReadableAccountId(principal, accountId);
        authOk = true;
      } finally {
        hotPathMetrics.recordOutcome(
            endpointIndex,
            TransactionReadHotPathMetrics.STAGE_AUTHORIZATION_INDEX,
            authStart,
            authOk);
      }
      TransactionCursor decodedCursor =
          cursor == null || cursor.isBlank()
              ? null
              : TransactionCursorCodec.decode(resolvedAccountId, cursor);
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
      long usecaseStart = hotPathMetrics.startTimestamp();
      boolean usecaseOk = false;
      int fairnessSlot = -1;
      TransactionSlice slice;
      try {
        fairnessSlot = accountFairnessLimiter.acquire(resolvedAccountId);
        slice = singleFlight.get(endpoint, query, () -> queryExecutor.apply(query));
        usecaseOk = true;
      } finally {
        if (fairnessSlot >= 0) {
          accountFairnessLimiter.release(fairnessSlot);
        }
        hotPathMetrics.recordOutcome(
            endpointIndex,
            TransactionReadHotPathMetrics.STAGE_USECASE_INDEX,
            usecaseStart,
            usecaseOk);
      }
      long mappingStart = hotPathMetrics.startTimestamp();
      boolean mappingOk = false;
      try {
        TransactionQueryResponse response =
            TransactionQueryResponse.from(resolvedAccountId, slice, resolvedResponseShape);
        mappingOk = true;
        totalOk = true;
        return response;
      } finally {
        hotPathMetrics.recordOutcome(
            endpointIndex,
            TransactionReadHotPathMetrics.STAGE_RESPONSE_MAPPING_INDEX,
            mappingStart,
            mappingOk);
      }
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    } finally {
      hotPathMetrics.recordOutcome(
          endpointIndex, TransactionReadHotPathMetrics.STAGE_TOTAL_INDEX, totalStart, totalOk);
    }
  }
}
