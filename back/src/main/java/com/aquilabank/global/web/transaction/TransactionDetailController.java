package com.aquilabank.global.web.transaction;

import com.aquilabank.domain.transaction.model.TransactionDetail;
import com.aquilabank.domain.transaction.model.TransactionDetailQuery;
import com.aquilabank.domain.transaction.usecase.TransactionDetailQueryUseCase;
import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipal;
import com.aquilabank.global.web.security.RequestAccountAuthorizationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.annotation.Autowired;
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
      RequestAccountAuthorizationService requestAccountAuthorizationService) {
    this(
        transactionDetailQueryUseCase,
        requestAccountAuthorizationService,
        new TransactionReadHotPathMetrics(new SimpleMeterRegistry()),
        new TransactionReadSingleFlight(),
        new TransactionReadAccountFairnessLimiter(2));
  }

  @Autowired
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
    return hotPathMetrics.record(
        TransactionReadHotPathMetrics.ENDPOINT_DETAIL,
        TransactionReadHotPathMetrics.STAGE_TOTAL,
        () -> getTransactionDetailMeasured(principal, transactionReference, accountId));
  }

  private TransactionDetailResponse getTransactionDetailMeasured(
      AuthenticatedRequestPrincipal principal, String transactionReference, long accountId) {
    try {
      // 권한 실패를 먼저 끊어 타 계좌 거래 존재 여부를 상세 응답으로 노출하지 않습니다.
      long resolvedAccountId =
          hotPathMetrics.record(
              TransactionReadHotPathMetrics.ENDPOINT_DETAIL,
              TransactionReadHotPathMetrics.STAGE_AUTHORIZATION,
              () ->
                  requestAccountAuthorizationService.resolveReadableAccountId(
                      principal, accountId));
      TransactionDetailQuery query =
          new TransactionDetailQuery(resolvedAccountId, transactionReference);
      TransactionDetail detail =
          hotPathMetrics.record(
              TransactionReadHotPathMetrics.ENDPOINT_DETAIL,
              TransactionReadHotPathMetrics.STAGE_USECASE,
              () ->
                  accountFairnessLimiter.execute(
                      resolvedAccountId,
                      () ->
                          singleFlight.execute(
                              TransactionReadHotPathMetrics.ENDPOINT_DETAIL,
                              query,
                              () -> transactionDetailQueryUseCase.getTransactionDetail(query))));
      return hotPathMetrics.record(
          TransactionReadHotPathMetrics.ENDPOINT_DETAIL,
          TransactionReadHotPathMetrics.STAGE_RESPONSE_MAPPING,
          () -> TransactionDetailResponse.from(detail));
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }
  }
}
