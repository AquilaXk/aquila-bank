package com.aquilabank.global.web.transaction;

import com.aquilabank.domain.transaction.model.TransactionDetail;
import com.aquilabank.domain.transaction.model.TransactionDetailQuery;
import com.aquilabank.domain.transaction.usecase.TransactionDetailQueryUseCase;
import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipal;
import com.aquilabank.global.web.security.RequestAccountAuthorizationService;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 거래 상세 drill-down exact lookup을 노출하는 HTTP adapter */
@Validated
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionDetailController {

  private final TransactionDetailQueryUseCase transactionDetailQueryUseCase;
  private final RequestAccountAuthorizationService requestAccountAuthorizationService;
  private final TransactionReadHotPathMetrics hotPathMetrics;
  private final TransactionReadSingleFlight singleFlight;
  private final TransactionReadAccountFairnessLimiter accountFairnessLimiter;

  public TransactionDetailController(
      TransactionDetailQueryUseCase transactionDetailQueryUseCase,
      RequestAccountAuthorizationService requestAccountAuthorizationService,
      TransactionReadHotPathMetrics hotPathMetrics,
      TransactionReadSingleFlight singleFlight,
      TransactionReadAccountFairnessLimiter accountFairnessLimiter) {
    this.transactionDetailQueryUseCase = transactionDetailQueryUseCase;
    this.requestAccountAuthorizationService = requestAccountAuthorizationService;
    this.hotPathMetrics = hotPathMetrics;
    this.singleFlight = singleFlight;
    this.accountFairnessLimiter = accountFairnessLimiter;
  }

  @GetMapping("/{transactionReference}")
  public TransactionDetailResponse getTransactionDetail(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @PathVariable String transactionReference,
      @RequestParam @Positive(message = "accountId must be positive") long accountId) {
    long totalStart = hotPathMetrics.startTimestamp();
    boolean totalOk = false;
    try {
      // 권한 실패를 먼저 끊어 타 계좌 거래 존재 여부를 상세 응답으로 노출하지 않습니다.
      long authStart = hotPathMetrics.startTimestamp();
      boolean authOk = false;
      long resolvedAccountId;
      try {
        resolvedAccountId =
            requestAccountAuthorizationService.resolveReadableAccountId(principal, accountId);
        authOk = true;
      } finally {
        hotPathMetrics.recordOutcome(
            TransactionReadHotPathMetrics.ENDPOINT_DETAIL_INDEX,
            TransactionReadHotPathMetrics.STAGE_AUTHORIZATION_INDEX,
            authStart,
            authOk);
      }
      TransactionDetailQuery query =
          new TransactionDetailQuery(resolvedAccountId, transactionReference);
      long usecaseStart = hotPathMetrics.startTimestamp();
      boolean usecaseOk = false;
      int fairnessSlot = -1;
      TransactionDetail detail;
      try {
        fairnessSlot = accountFairnessLimiter.acquire(resolvedAccountId);
        detail =
            singleFlight.execute(
                TransactionReadHotPathMetrics.ENDPOINT_DETAIL,
                query,
                () -> transactionDetailQueryUseCase.getTransactionDetail(query));
        usecaseOk = true;
      } finally {
        if (fairnessSlot >= 0) {
          accountFairnessLimiter.release(fairnessSlot);
        }
        hotPathMetrics.recordOutcome(
            TransactionReadHotPathMetrics.ENDPOINT_DETAIL_INDEX,
            TransactionReadHotPathMetrics.STAGE_USECASE_INDEX,
            usecaseStart,
            usecaseOk);
      }
      long mappingStart = hotPathMetrics.startTimestamp();
      boolean mappingOk = false;
      try {
        TransactionDetailResponse response = TransactionDetailResponse.from(detail);
        mappingOk = true;
        totalOk = true;
        return response;
      } finally {
        hotPathMetrics.recordOutcome(
            TransactionReadHotPathMetrics.ENDPOINT_DETAIL_INDEX,
            TransactionReadHotPathMetrics.STAGE_RESPONSE_MAPPING_INDEX,
            mappingStart,
            mappingOk);
      }
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    } finally {
      hotPathMetrics.recordOutcome(
          TransactionReadHotPathMetrics.ENDPOINT_DETAIL_INDEX,
          TransactionReadHotPathMetrics.STAGE_TOTAL_INDEX,
          totalStart,
          totalOk);
    }
  }
}
