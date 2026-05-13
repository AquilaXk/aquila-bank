package com.aquilabank.global.web.customerapplication;

import com.aquilabank.domain.customerapplication.model.CustomerApplicationSubmission;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationSubmitCommand;
import com.aquilabank.domain.customerapplication.model.CustomerApplicationType;
import com.aquilabank.domain.customerapplication.usecase.CustomerApplicationSubmitUseCase;
import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipal;
import com.aquilabank.global.web.security.RequestAccountAuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 고객센터/인증센터/부가업무 신청 접수 public API입니다. */
@Validated
@RestController
@RequestMapping("/api/v1/customer-service/applications")
public class CustomerApplicationController {

  private static final int MAX_PAYLOAD_FIELDS = 30;

  private final CustomerApplicationSubmitUseCase customerApplicationSubmitUseCase;
  private final RequestAccountAuthorizationService requestAccountAuthorizationService;

  public CustomerApplicationController(
      CustomerApplicationSubmitUseCase customerApplicationSubmitUseCase,
      RequestAccountAuthorizationService requestAccountAuthorizationService) {
    this.customerApplicationSubmitUseCase = customerApplicationSubmitUseCase;
    this.requestAccountAuthorizationService = requestAccountAuthorizationService;
  }

  @PostMapping
  public CustomerApplicationResponse submit(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @RequestHeader("Idempotency-Key") @Size(max = 120) String idempotencyKey,
      @Valid @RequestBody CustomerApplicationRequest request) {
    AuthenticatedUserPrincipal userPrincipal = requireUserPrincipal(principal);
    Long accountId = resolveAccountId(principal, request.applicationType(), request.accountId());
    validatePayload(request.payload());
    CustomerApplicationSubmission result =
        customerApplicationSubmitUseCase.submit(
            new CustomerApplicationSubmitCommand(
                userPrincipal.userId(),
                accountId,
                request.applicationType(),
                idempotencyKey,
                request.totpCode(),
                request.payload()));
    return CustomerApplicationResponse.from(result);
  }

  private Long resolveAccountId(
      AuthenticatedRequestPrincipal principal,
      CustomerApplicationType applicationType,
      Long accountId) {
    if (accountId == null) {
      return null;
    }
    if (applicationType == CustomerApplicationType.TRANSFER_LIMIT_CHANGE) {
      return requestAccountAuthorizationService.resolveTransferSourceAccountId(
          principal, accountId);
    }
    return requestAccountAuthorizationService.resolveReadableAccountId(principal, accountId);
  }

  private AuthenticatedUserPrincipal requireUserPrincipal(AuthenticatedRequestPrincipal principal) {
    if (principal instanceof AuthenticatedUserPrincipal userPrincipal) {
      return userPrincipal;
    }
    throw new ResponseStatusException(
        org.springframework.http.HttpStatus.UNAUTHORIZED, "user authentication required");
  }

  private void validatePayload(Map<String, Object> payload) {
    if (payload == null) {
      throw new IllegalArgumentException("payload is required");
    }
    if (payload.size() > MAX_PAYLOAD_FIELDS) {
      throw new IllegalArgumentException("payload has too many fields");
    }
    payload.keySet().forEach(this::validatePayloadKey);
  }

  private void validatePayloadKey(String key) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("payload key is required");
    }
    String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    if (normalized.contains("password")
        || normalized.contains("secret")
        || normalized.contains("privatekey")
        || normalized.contains("totpcode")) {
      throw new IllegalArgumentException("payload must not contain secret fields");
    }
  }

  public record CustomerApplicationRequest(
      @NotNull(message = "applicationType is required") CustomerApplicationType applicationType,
      @Positive(message = "accountId must be positive") Long accountId,
      @Size(max = 12, message = "totpCode must be 12 characters or less") String totpCode,
      @NotNull(message = "payload is required") Map<String, Object> payload) {}

  public record CustomerApplicationResponse(
      String applicationReference,
      Long accountId,
      String applicationType,
      String status,
      boolean mfaVerified,
      Instant mfaVerifiedAt,
      Instant submittedAt,
      Instant updatedAt) {

    static CustomerApplicationResponse from(CustomerApplicationSubmission result) {
      return new CustomerApplicationResponse(
          result.applicationReference(),
          result.accountId(),
          result.applicationType().name(),
          result.status().name(),
          result.mfaVerified(),
          result.mfaVerifiedAt(),
          result.submittedAt(),
          result.updatedAt());
    }
  }
}
