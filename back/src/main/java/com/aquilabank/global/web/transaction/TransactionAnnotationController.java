package com.aquilabank.global.web.transaction;

import com.aquilabank.domain.transaction.model.SaveTransactionAnnotationCommand;
import com.aquilabank.domain.transaction.model.TransactionAnnotation;
import com.aquilabank.domain.transaction.model.TransactionCategory;
import com.aquilabank.domain.transaction.usecase.TransactionAnnotationUseCase;
import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.web.security.AuthenticatedPrincipals;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipal;
import com.aquilabank.global.web.security.RequestAccountAuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 거래 내역 사용자 분류 및 메모 배치 조회/저장 API 컨트롤러입니다. */
@Validated
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionAnnotationController {

  private final TransactionAnnotationUseCase annotationUseCase;
  private final RequestAccountAuthorizationService requestAccountAuthorizationService;

  public TransactionAnnotationController(
      TransactionAnnotationUseCase annotationUseCase,
      RequestAccountAuthorizationService requestAccountAuthorizationService) {
    this.annotationUseCase = annotationUseCase;
    this.requestAccountAuthorizationService = requestAccountAuthorizationService;
  }

  @GetMapping("/annotations")
  public TransactionAnnotationListResponse list(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @RequestParam @Positive(message = "accountId must be positive") long accountId,
      @RequestParam(defaultValue = "") @Size(max = 50, message = "references must be 50 or less") List<String> references) {
    AuthenticatedUserPrincipal userPrincipal =
        AuthenticatedPrincipals.requireUserPrincipal(principal);
    long resolvedAccountId =
        requestAccountAuthorizationService.resolveReadableAccountId(principal, accountId);
    List<TransactionAnnotationResponse> items =
        annotationUseCase
            .findByReferences(userPrincipal.userId(), resolvedAccountId, references)
            .stream()
            .map(TransactionAnnotationResponse::from)
            .toList();
    return new TransactionAnnotationListResponse(items);
  }

  @PutMapping("/{transactionReference}/annotation")
  public TransactionAnnotationResponse save(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @PathVariable @Size(max = 80, message = "transactionReference must be 80 characters or less") String transactionReference,
      @Valid @RequestBody SaveTransactionAnnotationRequest request) {
    AuthenticatedUserPrincipal userPrincipal =
        AuthenticatedPrincipals.requireUserPrincipal(principal);
    long resolvedAccountId =
        requestAccountAuthorizationService.resolveReadableAccountId(principal, request.accountId());
    TransactionAnnotation saved =
        annotationUseCase.save(
            new SaveTransactionAnnotationCommand(
                userPrincipal.userId(),
                resolvedAccountId,
                transactionReference,
                request.category(),
                request.memo()));
    return TransactionAnnotationResponse.from(saved);
  }

  public record SaveTransactionAnnotationRequest(
      @Positive(message = "accountId must be positive") long accountId,
      @NotNull(message = "category is required") TransactionCategory category,
      @Size(max = 200, message = "memo must be 200 characters or less") String memo) {}

  public record TransactionAnnotationListResponse(List<TransactionAnnotationResponse> items) {}

  public record TransactionAnnotationResponse(
      long accountId,
      String transactionReference,
      String category,
      String memo,
      Instant updatedAt) {

    static TransactionAnnotationResponse from(TransactionAnnotation item) {
      return new TransactionAnnotationResponse(
          item.accountId(),
          item.transactionReference(),
          item.category().name(),
          item.memo(),
          item.updatedAt());
    }
  }
}
