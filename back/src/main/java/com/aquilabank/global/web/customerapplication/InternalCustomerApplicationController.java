package com.aquilabank.global.web.customerapplication;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationAction;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationDecisionCommand;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationDetails;
import com.aquilabank.domain.customerapplication.usecase.CustomerApplicationOperationUseCase;
import com.aquilabank.global.security.InternalServiceRequestAuthorizer;
import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenClaims;
import com.aquilabank.global.web.RequestTraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 내부 운영 도구가 고객 신청을 검토/승인/실행하는 adapter입니다. */
@Validated
@RestController
@RequestMapping("/internal/api/v1/customer-service/applications")
public class InternalCustomerApplicationController {

  private final CustomerApplicationOperationUseCase operationUseCase;
  private final InternalServiceRequestAuthorizer internalServiceRequestAuthorizer;

  public InternalCustomerApplicationController(
      CustomerApplicationOperationUseCase operationUseCase,
      InternalServiceRequestAuthorizer internalServiceRequestAuthorizer) {
    this.operationUseCase = operationUseCase;
    this.internalServiceRequestAuthorizer = internalServiceRequestAuthorizer;
  }

  @PostMapping("/{applicationReference}/review")
  public CustomerApplicationOperationResponse startReview(
      HttpServletRequest httpServletRequest,
      @PathVariable String applicationReference,
      @Valid @RequestBody(required = false) CustomerApplicationOperationRequest request) {
    return apply(
        httpServletRequest, applicationReference, CustomerApplicationAction.START_REVIEW, request);
  }

  @PostMapping("/{applicationReference}/approve")
  public CustomerApplicationOperationResponse approve(
      HttpServletRequest httpServletRequest,
      @PathVariable String applicationReference,
      @Valid @RequestBody(required = false) CustomerApplicationOperationRequest request) {
    return apply(
        httpServletRequest, applicationReference, CustomerApplicationAction.APPROVE, request);
  }

  @PostMapping("/{applicationReference}/reject")
  public CustomerApplicationOperationResponse reject(
      HttpServletRequest httpServletRequest,
      @PathVariable String applicationReference,
      @Valid @RequestBody(required = false) CustomerApplicationOperationRequest request) {
    return apply(
        httpServletRequest, applicationReference, CustomerApplicationAction.REJECT, request);
  }

  @PostMapping("/{applicationReference}/cancel")
  public CustomerApplicationOperationResponse cancel(
      HttpServletRequest httpServletRequest,
      @PathVariable String applicationReference,
      @Valid @RequestBody(required = false) CustomerApplicationOperationRequest request) {
    return apply(
        httpServletRequest, applicationReference, CustomerApplicationAction.CANCEL, request);
  }

  @PostMapping("/{applicationReference}/execute")
  public CustomerApplicationOperationResponse execute(
      HttpServletRequest httpServletRequest,
      @PathVariable String applicationReference,
      @Valid @RequestBody(required = false) CustomerApplicationOperationRequest request) {
    return apply(
        httpServletRequest, applicationReference, CustomerApplicationAction.EXECUTE, request);
  }

  private CustomerApplicationOperationResponse apply(
      HttpServletRequest httpServletRequest,
      String applicationReference,
      CustomerApplicationAction action,
      CustomerApplicationOperationRequest request) {
    InternalServiceTokenClaims claims =
        internalServiceRequestAuthorizer.requireScope(
            httpServletRequest, InternalServiceScope.CUSTOMER_APPLICATION_OPS);
    CustomerApplicationDetails result =
        operationUseCase.apply(
            new CustomerApplicationDecisionCommand(
                applicationReference,
                action,
                claims.subject(),
                request == null ? null : request.reason(),
                resolveRequestId(httpServletRequest)));
    return CustomerApplicationOperationResponse.from(result);
  }

  private String resolveRequestId(HttpServletRequest httpServletRequest) {
    return RequestTraceContext.currentRequestId()
        .orElseGet(
            () -> {
              String requestId =
                  httpServletRequest.getHeader(RequestTraceContext.REQUEST_ID_HEADER);
              if (requestId == null || requestId.isBlank()) {
                throw new IllegalStateException("requestId is not initialized");
              }
              return requestId;
            });
  }

  public record CustomerApplicationOperationRequest(
      @Size(max = 300, message = "reason must be 300 characters or less") String reason) {}

  public record CustomerApplicationOperationResponse(
      String applicationReference,
      long userId,
      Long accountId,
      String applicationType,
      String status,
      boolean mfaVerified,
      Instant mfaVerifiedAt,
      Instant submittedAt,
      Instant updatedAt,
      String reason,
      String processedBy,
      Instant processedAt,
      Map<String, Object> executionResult) {

    static CustomerApplicationOperationResponse from(CustomerApplicationDetails result) {
      return new CustomerApplicationOperationResponse(
          result.applicationReference(),
          result.userId(),
          result.accountId(),
          result.applicationType().name(),
          result.status().name(),
          result.mfaVerified(),
          result.mfaVerifiedAt(),
          result.submittedAt(),
          result.updatedAt(),
          result.reason(),
          result.processedBy(),
          result.processedAt(),
          result.executionResult());
    }
  }
}
