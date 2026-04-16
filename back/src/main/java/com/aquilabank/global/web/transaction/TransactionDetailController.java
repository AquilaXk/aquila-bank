package com.aquilabank.global.web.transaction;

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

  public TransactionDetailController(
      TransactionDetailQueryUseCase transactionDetailQueryUseCase,
      RequestAccountAuthorizationService requestAccountAuthorizationService) {
    this.transactionDetailQueryUseCase = transactionDetailQueryUseCase;
    this.requestAccountAuthorizationService = requestAccountAuthorizationService;
  }

  @GetMapping("/{transactionReference}")
  public TransactionDetailResponse getTransactionDetail(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @PathVariable String transactionReference,
      @RequestParam @Positive(message = "accountId must be positive") long accountId) {
    try {
      // 권한 실패를 먼저 끊어 타 계좌 거래 존재 여부를 상세 응답으로 노출하지 않습니다.
      long resolvedAccountId =
          requestAccountAuthorizationService.resolveReadableAccountId(principal, accountId);
      return TransactionDetailResponse.from(
          transactionDetailQueryUseCase.getTransactionDetail(
              new TransactionDetailQuery(resolvedAccountId, transactionReference)));
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage(), ex);
    }
  }
}
