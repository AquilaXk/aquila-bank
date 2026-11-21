package com.aquilabank.global.web.ledger;

import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.usecase.AccountSummaryQueryUseCase;
import com.aquilabank.domain.auth.model.TotpOperationVerifyCommand;
import com.aquilabank.domain.auth.usecase.TotpOperationRequirementUseCase;
import com.aquilabank.domain.auth.usecase.TotpOperationVerifyUseCase;
import com.aquilabank.domain.ledger.model.CreateScheduledTransferCommand;
import com.aquilabank.domain.ledger.model.RecipientPreviewThrottleDecision;
import com.aquilabank.domain.ledger.model.ScheduledTransferFrequency;
import com.aquilabank.domain.ledger.model.ScheduledTransferRule;
import com.aquilabank.domain.ledger.model.ScheduledTransferStatus;
import com.aquilabank.domain.ledger.port.RecipientPreviewThrottlePort;
import com.aquilabank.domain.ledger.usecase.ScheduledTransferUseCase;
import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.web.security.AuthenticatedPrincipals;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipal;
import com.aquilabank.global.web.security.RequestAccountAuthorizationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 예약이체 및 정기 자동이체 규칙 관리 API 컨트롤러입니다. */
@Validated
@RestController
@RequestMapping("/api/v1/transfers/schedules")
public class ScheduledTransferController {

  private static final int USER_RECIPIENT_PREVIEW_MAX_ATTEMPTS = 20;
  private static final long USER_RECIPIENT_PREVIEW_WINDOW_SECONDS = 60L;

  private final ScheduledTransferUseCase scheduledTransferUseCase;
  private final RequestAccountAuthorizationService requestAccountAuthorizationService;
  private final AccountSummaryQueryUseCase accountSummaryQueryUseCase;
  private final RecipientPreviewThrottlePort recipientPreviewThrottlePort;
  private final TotpOperationRequirementUseCase totpOperationRequirementUseCase;
  private final TotpOperationVerifyUseCase totpOperationVerifyUseCase;
  private final Clock clock;

  @Autowired
  public ScheduledTransferController(
      ScheduledTransferUseCase scheduledTransferUseCase,
      RequestAccountAuthorizationService requestAccountAuthorizationService,
      AccountSummaryQueryUseCase accountSummaryQueryUseCase,
      RecipientPreviewThrottlePort recipientPreviewThrottlePort,
      TotpOperationRequirementUseCase totpOperationRequirementUseCase,
      TotpOperationVerifyUseCase totpOperationVerifyUseCase) {
    this(
        scheduledTransferUseCase,
        requestAccountAuthorizationService,
        accountSummaryQueryUseCase,
        recipientPreviewThrottlePort,
        totpOperationRequirementUseCase,
        totpOperationVerifyUseCase,
        Clock.systemUTC());
  }

  ScheduledTransferController(
      ScheduledTransferUseCase scheduledTransferUseCase,
      RequestAccountAuthorizationService requestAccountAuthorizationService,
      AccountSummaryQueryUseCase accountSummaryQueryUseCase,
      RecipientPreviewThrottlePort recipientPreviewThrottlePort,
      TotpOperationRequirementUseCase totpOperationRequirementUseCase,
      TotpOperationVerifyUseCase totpOperationVerifyUseCase,
      Clock clock) {
    this.scheduledTransferUseCase = scheduledTransferUseCase;
    this.requestAccountAuthorizationService = requestAccountAuthorizationService;
    this.accountSummaryQueryUseCase = accountSummaryQueryUseCase;
    this.recipientPreviewThrottlePort = recipientPreviewThrottlePort;
    this.totpOperationRequirementUseCase = totpOperationRequirementUseCase;
    this.totpOperationVerifyUseCase = totpOperationVerifyUseCase;
    this.clock = clock;
  }

  @GetMapping
  public ScheduledTransferListResponse list(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal) {
    AuthenticatedUserPrincipal userPrincipal =
        AuthenticatedPrincipals.requireUserPrincipal(principal);
    List<ScheduledTransferResponse> items =
        scheduledTransferUseCase.list(userPrincipal.userId()).stream()
            .map(ScheduledTransferResponse::from)
            .toList();
    return new ScheduledTransferListResponse(items);
  }

  @PostMapping
  public ScheduledTransferResponse create(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @Valid @RequestBody CreateScheduledTransferRequest request) {
    AuthenticatedUserPrincipal userPrincipal =
        AuthenticatedPrincipals.requireUserPrincipal(principal);
    long resolvedSourceAccountId =
        requestAccountAuthorizationService.resolveTransferSourceAccountId(
            principal, request.sourceAccountId());
    AccountSummary source = accountSummaryQueryUseCase.getByAccountId(resolvedSourceAccountId);
    if (!source.currencyCode().equals(request.currencyCode())) {
      throw new IllegalArgumentException("currencyCode mismatch");
    }
    // TOTP 검증과 수취계좌 조회 스로틀을 타인 계좌번호 조회보다 먼저 수행해 계좌번호 열거 오라클을 차단합니다.
    verifyTotpIfRequired(userPrincipal.userId(), request.totpCode());
    consumeLookupThrottle(userPrincipal.userId());
    AccountSummary target =
        accountSummaryQueryUseCase.getByAccountNumber(request.targetAccountNumber().trim());
    if (source.accountId() == target.accountId()) {
      throw new IllegalArgumentException("sourceAccountId and targetAccountId must differ");
    }
    if (!"ACTIVE".equals(target.accountStatus())) {
      throw new IllegalArgumentException("target account is not active");
    }
    if (!target.currencyCode().equals(request.currencyCode())) {
      throw new IllegalArgumentException("currencyCode mismatch");
    }
    ScheduledTransferRule rule =
        scheduledTransferUseCase.create(
            new CreateScheduledTransferCommand(
                userPrincipal.userId(),
                resolvedSourceAccountId,
                target.accountNumber(),
                request.amountMinor(),
                request.currencyCode(),
                request.summary(),
                request.scheduleType(),
                request.scheduledDay(),
                request.nextExecutionAt()));
    return ScheduledTransferResponse.from(rule);
  }

  @PostMapping("/{scheduleId}/status")
  public ScheduledTransferResponse updateStatus(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @PathVariable @Positive(message = "scheduleId must be positive") long scheduleId,
      @Valid @RequestBody UpdateScheduledTransferStatusRequest request) {
    AuthenticatedUserPrincipal userPrincipal =
        AuthenticatedPrincipals.requireUserPrincipal(principal);
    if (request.ruleStatus() == ScheduledTransferStatus.ACTIVE) {
      ScheduledTransferRule existing =
          scheduledTransferUseCase.get(userPrincipal.userId(), scheduleId);
      requestAccountAuthorizationService.resolveTransferSourceAccountId(
          principal, existing.sourceAccountId());
      AccountSummary source = accountSummaryQueryUseCase.getByAccountId(existing.sourceAccountId());
      if (!"ACTIVE".equals(source.accountStatus())) {
        throw new IllegalArgumentException("source account is not active");
      }
      verifyTotpIfRequired(userPrincipal.userId(), request.totpCode());
    }
    ScheduledTransferRule updated =
        scheduledTransferUseCase.updateStatus(
            userPrincipal.userId(), scheduleId, request.ruleStatus());
    return ScheduledTransferResponse.from(updated);
  }

  private void verifyTotpIfRequired(long userId, String totpCode) {
    if (totpOperationRequirementUseCase.requiresVerification(userId)) {
      totpOperationVerifyUseCase.verify(new TotpOperationVerifyCommand(userId, totpCode));
    }
  }

  private void consumeLookupThrottle(long userId) {
    RecipientPreviewThrottleDecision decision =
        recipientPreviewThrottlePort.consume(
            userId,
            clock.instant(),
            USER_RECIPIENT_PREVIEW_MAX_ATTEMPTS,
            USER_RECIPIENT_PREVIEW_WINDOW_SECONDS);
    if (!decision.permitted()) {
      throw new TransferRecipientPreviewThrottledException(decision.retryAfterSeconds());
    }
  }

  public record CreateScheduledTransferRequest(
      @Positive(message = "sourceAccountId must be positive") long sourceAccountId,
      @NotBlank(message = "targetAccountNumber is required") @Pattern(regexp = "^[0-9-]{6,40}$", message = "targetAccountNumber format is invalid") String targetAccountNumber,
      @Positive(message = "amountMinor must be positive") long amountMinor,
      @NotBlank(message = "currencyCode is required") @Pattern(
              regexp = "^[A-Z]{3}$",
              message = "currencyCode must be a 3-letter uppercase code")
          String currencyCode,
      @NotBlank(message = "summary is required") @Size(max = 120, message = "summary must be 120 characters or less") String summary,
      @NotNull(message = "scheduleType is required") ScheduledTransferFrequency scheduleType,
      @Min(value = 1, message = "scheduledDay must be between 1 and 31") @Max(value = 31, message = "scheduledDay must be between 1 and 31") int scheduledDay,
      @NotNull(message = "nextExecutionAt is required") Instant nextExecutionAt,
      @Size(max = 12, message = "totpCode must be 12 characters or less") String totpCode) {}

  public record UpdateScheduledTransferStatusRequest(
      @NotNull(message = "ruleStatus is required") ScheduledTransferStatus ruleStatus,
      @Size(max = 12, message = "totpCode must be 12 characters or less") String totpCode) {}

  public record ScheduledTransferListResponse(List<ScheduledTransferResponse> items) {}

  public record ScheduledTransferResponse(
      long scheduleId,
      long sourceAccountId,
      String targetAccountNumber,
      long amountMinor,
      String currencyCode,
      String summary,
      String scheduleType,
      int scheduledDay,
      Instant nextExecutionAt,
      String ruleStatus,
      Instant createdAt,
      Instant updatedAt) {

    static ScheduledTransferResponse from(ScheduledTransferRule item) {
      return new ScheduledTransferResponse(
          item.scheduleId(),
          item.sourceAccountId(),
          item.targetAccountNumber(),
          item.amountMinor(),
          item.currencyCode(),
          item.summary(),
          item.scheduleType().name(),
          item.scheduledDay(),
          item.nextExecutionAt(),
          item.ruleStatus().name(),
          item.createdAt(),
          item.updatedAt());
    }
  }
}
