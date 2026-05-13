package com.aquilabank.global.web.ledger;

import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.usecase.AccountSummaryQueryUseCase;
import com.aquilabank.domain.auth.model.TotpOperationVerifyCommand;
import com.aquilabank.domain.auth.usecase.TotpOperationRequirementUseCase;
import com.aquilabank.domain.auth.usecase.TotpOperationVerifyUseCase;
import com.aquilabank.domain.ledger.model.TransferCommand;
import com.aquilabank.domain.ledger.model.TransferLimitPolicy;
import com.aquilabank.domain.ledger.model.TransferResult;
import com.aquilabank.domain.ledger.model.TransferReversalCommand;
import com.aquilabank.domain.ledger.model.TransferReversalReason;
import com.aquilabank.domain.ledger.model.TransferReversalResult;
import com.aquilabank.domain.ledger.port.TransferLimitUsageReadPort;
import com.aquilabank.domain.ledger.usecase.TransferCommandUseCase;
import com.aquilabank.domain.ledger.usecase.TransferLimitPolicyUseCase;
import com.aquilabank.domain.ledger.usecase.TransferReversalUseCase;
import com.aquilabank.global.config.TransferLimitPolicyProperties;
import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipal;
import com.aquilabank.global.web.security.RequestAccountAuthorizationService;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 송금 명령 use case를 노출하는 HTTP adapter */
@Validated
@RestController
@RequestMapping("/api/v1/transfers")
public class TransferCommandController {

  private static final int USER_RECIPIENT_PREVIEW_MAX_ATTEMPTS = 20;
  private static final long USER_RECIPIENT_PREVIEW_WINDOW_SECONDS = 60L;
  private static final int USER_RECIPIENT_PREVIEW_MAX_TRACKED_USERS = 10_000;

  private final TransferCommandUseCase transferCommandUseCase;
  private final TransferReversalUseCase transferReversalUseCase;
  private final RequestAccountAuthorizationService requestAccountAuthorizationService;
  private final AccountSummaryQueryUseCase accountSummaryQueryUseCase;
  private final TransferLimitUsageReadPort transferLimitUsageReadPort;
  private final TransferLimitPolicyUseCase transferLimitPolicyUseCase;
  private final TotpOperationRequirementUseCase totpOperationRequirementUseCase;
  private final TotpOperationVerifyUseCase totpOperationVerifyUseCase;
  private final TransferLimitPolicyProperties transferLimitPolicyProperties;
  private final Clock clock;
  private final ConcurrentMap<Long, RecipientPreviewThrottleWindow>
      recipientPreviewThrottleWindows = new ConcurrentHashMap<>();

  @Autowired
  public TransferCommandController(
      TransferCommandUseCase transferCommandUseCase,
      TransferReversalUseCase transferReversalUseCase,
      RequestAccountAuthorizationService requestAccountAuthorizationService,
      AccountSummaryQueryUseCase accountSummaryQueryUseCase,
      TransferLimitUsageReadPort transferLimitUsageReadPort,
      TransferLimitPolicyUseCase transferLimitPolicyUseCase,
      TotpOperationRequirementUseCase totpOperationRequirementUseCase,
      TotpOperationVerifyUseCase totpOperationVerifyUseCase,
      TransferLimitPolicyProperties transferLimitPolicyProperties) {
    this(
        transferCommandUseCase,
        transferReversalUseCase,
        requestAccountAuthorizationService,
        accountSummaryQueryUseCase,
        transferLimitUsageReadPort,
        transferLimitPolicyUseCase,
        totpOperationRequirementUseCase,
        totpOperationVerifyUseCase,
        transferLimitPolicyProperties,
        Clock.systemUTC());
  }

  TransferCommandController(
      TransferCommandUseCase transferCommandUseCase,
      TransferReversalUseCase transferReversalUseCase,
      RequestAccountAuthorizationService requestAccountAuthorizationService,
      AccountSummaryQueryUseCase accountSummaryQueryUseCase,
      TransferLimitUsageReadPort transferLimitUsageReadPort,
      TransferLimitPolicyUseCase transferLimitPolicyUseCase,
      TotpOperationRequirementUseCase totpOperationRequirementUseCase,
      TotpOperationVerifyUseCase totpOperationVerifyUseCase,
      TransferLimitPolicyProperties transferLimitPolicyProperties,
      Clock clock) {
    this.transferCommandUseCase = transferCommandUseCase;
    this.transferReversalUseCase = transferReversalUseCase;
    this.requestAccountAuthorizationService = requestAccountAuthorizationService;
    this.accountSummaryQueryUseCase = accountSummaryQueryUseCase;
    this.transferLimitUsageReadPort = transferLimitUsageReadPort;
    this.transferLimitPolicyUseCase = transferLimitPolicyUseCase;
    this.totpOperationRequirementUseCase = totpOperationRequirementUseCase;
    this.totpOperationVerifyUseCase = totpOperationVerifyUseCase;
    this.transferLimitPolicyProperties = transferLimitPolicyProperties;
    this.clock = clock;
  }

  @GetMapping("/preview")
  public TransferPreviewResponse preview(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @RequestParam @Positive(message = "sourceAccountId must be positive") long sourceAccountId,
      @RequestParam(required = false) @Positive(message = "targetAccountId must be positive") Long targetAccountId,
      @RequestParam(required = false)
          @Size(max = 20, message = "targetAccountNumber must be 20 characters or less") String targetAccountNumber,
      @RequestParam @Positive(message = "amountMinor must be positive") long amountMinor,
      @RequestParam
          @NotBlank(message = "currencyCode is required") @Pattern(
              regexp = "^[A-Z]{3}$",
              message = "currencyCode must be a 3-letter uppercase code")
          String currencyCode) {
    long resolvedSourceAccountId =
        requestAccountAuthorizationService.resolveTransferSourceAccountId(
            principal, sourceAccountId);
    AccountSummary source = accountSummaryQueryUseCase.getByAccountId(resolvedSourceAccountId);
    AccountSummary target =
        resolveTransferPreviewTargetAccount(principal, targetAccountId, targetAccountNumber);
    ZoneId businessZoneId = ZoneId.of(transferLimitPolicyProperties.businessZoneId());
    Instant now = clock.instant();
    LocalDate businessDate = LocalDate.ofInstant(now, businessZoneId);
    Instant fromInclusive = businessDate.atStartOfDay(businessZoneId).toInstant();
    Instant toExclusive = businessDate.plusDays(1).atStartOfDay(businessZoneId).toInstant();
    long dailyUsedMinor =
        transferLimitUsageReadPort.sumBookedDebitAmountMinor(
            resolvedSourceAccountId, currencyCode, fromInclusive, toExclusive);
    TransferLimitPolicy policy = transferLimitPolicyUseCase.resolvePolicy(resolvedSourceAccountId);
    long feeMinor = 0L;
    long totalDebitMinor = amountMinor + feeMinor;
    long dailyRemainingMinor = Math.max(0L, policy.dailyTransferLimitMinor() - dailyUsedMinor);
    String blockedReason =
        resolvePreviewBlockedReason(
            source, target, amountMinor, totalDebitMinor, dailyUsedMinor, currencyCode, policy);
    return new TransferPreviewResponse(
        resolvedSourceAccountId,
        TransferPreviewAccountResponse.from(target, shouldExposeInternalTarget(principal)),
        amountMinor,
        currencyCode,
        feeMinor,
        "INTERNAL_TRANSFER_WAIVED",
        totalDebitMinor,
        policy.singleTransferLimitMinor(),
        policy.dailyTransferLimitMinor(),
        dailyUsedMinor,
        dailyRemainingMinor,
        "OK".equals(blockedReason),
        blockedReason,
        resolveOtpRequired(principal));
  }

  @PostMapping
  public TransferResponse transfer(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody TransferRequest request) {
    long sourceAccountId =
        requestAccountAuthorizationService.resolveTransferSourceAccountId(
            principal, request.sourceAccountId());
    long targetAccountId =
        resolveTransferTargetAccountId(
            principal, request.targetAccountId(), request.targetAccountNumber());
    verifyOperationTotpIfRequired(principal, request.totpCode());
    TransferResult result =
        transferCommandUseCase.transfer(
            new TransferCommand(
                sourceAccountId,
                targetAccountId,
                request.amountMinor(),
                request.currencyCode(),
                request.summary(),
                idempotencyKey));
    return TransferResponse.from(result);
  }

  @PostMapping("/{transactionReference}/reversal")
  public TransferReversalResponse reverse(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @PathVariable String transactionReference,
      @RequestHeader("Idempotency-Key") String idempotencyKey,
      @Valid @RequestBody TransferReversalRequest request) {
    long sourceAccountId =
        requestAccountAuthorizationService.resolveTransferSourceAccountId(
            principal, request.sourceAccountId());
    verifyOperationTotpIfRequired(principal, request.totpCode());
    TransferReversalResult result =
        transferReversalUseCase.reverse(
            new TransferReversalCommand(
                transactionReference,
                sourceAccountId,
                request.amountMinor(),
                request.reversalReason(),
                request.summary(),
                idempotencyKey));
    return TransferReversalResponse.from(result);
  }

  private boolean resolveOtpRequired(AuthenticatedRequestPrincipal principal) {
    if (principal instanceof AuthenticatedUserPrincipal userPrincipal) {
      return totpOperationRequirementUseCase.requiresVerification(userPrincipal.userId());
    }
    return true;
  }

  private AccountSummary resolveTransferTargetAccount(
      AuthenticatedRequestPrincipal principal, Long targetAccountId, String targetAccountNumber) {
    if (principal instanceof AuthenticatedUserPrincipal) {
      if (targetAccountNumber == null || targetAccountNumber.isBlank()) {
        throw new IllegalArgumentException("targetAccountNumber is required");
      }
      return accountSummaryQueryUseCase.getByAccountNumber(targetAccountNumber.trim());
    }
    if (targetAccountId == null) {
      throw new IllegalArgumentException("targetAccountId is required");
    }
    return accountSummaryQueryUseCase.getByAccountId(targetAccountId);
  }

  private AccountSummary resolveTransferPreviewTargetAccount(
      AuthenticatedRequestPrincipal principal, Long targetAccountId, String targetAccountNumber) {
    if (principal instanceof AuthenticatedUserPrincipal userPrincipal) {
      if (targetAccountNumber == null || targetAccountNumber.isBlank()) {
        throw new IllegalArgumentException("targetAccountNumber is required");
      }
      checkRecipientPreviewThrottle(userPrincipal);
      return accountSummaryQueryUseCase.getByAccountNumber(targetAccountNumber.trim());
    }
    return resolveTransferTargetAccount(principal, targetAccountId, targetAccountNumber);
  }

  private long resolveTransferTargetAccountId(
      AuthenticatedRequestPrincipal principal, Long targetAccountId, String targetAccountNumber) {
    if (principal instanceof AuthenticatedUserPrincipal) {
      return resolveTransferTargetAccount(principal, targetAccountId, targetAccountNumber)
          .accountId();
    }
    if (targetAccountId == null) {
      throw new IllegalArgumentException("targetAccountId is required");
    }
    return targetAccountId;
  }

  private void checkRecipientPreviewThrottle(AuthenticatedUserPrincipal userPrincipal) {
    long nowEpochSecond = clock.instant().getEpochSecond();
    RecipientPreviewThrottleWindow window =
        recipientPreviewThrottleWindows.compute(
            userPrincipal.userId(),
            (userId, current) -> {
              if (current == null || current.expired(nowEpochSecond)) {
                return new RecipientPreviewThrottleWindow(nowEpochSecond, 1);
              }
              int attempts =
                  Math.min(current.attempts() + 1, USER_RECIPIENT_PREVIEW_MAX_ATTEMPTS + 1);
              return new RecipientPreviewThrottleWindow(current.windowStartEpochSecond(), attempts);
            });
    pruneExpiredRecipientPreviewWindows(nowEpochSecond);
    if (window.attempts() > USER_RECIPIENT_PREVIEW_MAX_ATTEMPTS) {
      throw new TransferRecipientPreviewThrottledException(
          window.retryAfterSeconds(nowEpochSecond));
    }
  }

  private void pruneExpiredRecipientPreviewWindows(long nowEpochSecond) {
    if (recipientPreviewThrottleWindows.size() <= USER_RECIPIENT_PREVIEW_MAX_TRACKED_USERS) {
      return;
    }
    // 사용자별 고정 window만 유지해 sequence 계좌번호 탐색 비용과 메모리 증가를 제한합니다.
    recipientPreviewThrottleWindows
        .entrySet()
        .removeIf(entry -> entry.getValue().expired(nowEpochSecond));
  }

  private boolean shouldExposeInternalTarget(AuthenticatedRequestPrincipal principal) {
    return !(principal instanceof AuthenticatedUserPrincipal);
  }

  private void verifyOperationTotpIfRequired(
      AuthenticatedRequestPrincipal principal, String totpCode) {
    if (!(principal instanceof AuthenticatedUserPrincipal userPrincipal)) {
      return;
    }
    if (!totpOperationRequirementUseCase.requiresVerification(userPrincipal.userId())) {
      return;
    }
    totpOperationVerifyUseCase.verify(
        new TotpOperationVerifyCommand(userPrincipal.userId(), totpCode));
  }

  private String resolvePreviewBlockedReason(
      AccountSummary source,
      AccountSummary target,
      long amountMinor,
      long totalDebitMinor,
      long dailyUsedMinor,
      String currencyCode,
      TransferLimitPolicy policy) {
    if (!source.currencyCode().equals(currencyCode)
        || !target.currencyCode().equals(currencyCode)) {
      return "CURRENCY_MISMATCH";
    }
    if (!"ACTIVE".equals(target.accountStatus())) {
      return "TARGET_ACCOUNT_NOT_ACTIVE";
    }
    if (amountMinor > policy.singleTransferLimitMinor()) {
      return "SINGLE_LIMIT_EXCEEDED";
    }
    if (dailyUsedMinor + amountMinor > policy.dailyTransferLimitMinor()) {
      return "DAILY_LIMIT_EXCEEDED";
    }
    if (totalDebitMinor > source.availableBalanceMinor()) {
      return "INSUFFICIENT_BALANCE";
    }
    return "OK";
  }

  private record RecipientPreviewThrottleWindow(long windowStartEpochSecond, int attempts) {

    boolean expired(long nowEpochSecond) {
      return nowEpochSecond - windowStartEpochSecond >= USER_RECIPIENT_PREVIEW_WINDOW_SECONDS;
    }

    long retryAfterSeconds(long nowEpochSecond) {
      return Math.max(
          1L, windowStartEpochSecond + USER_RECIPIENT_PREVIEW_WINDOW_SECONDS - nowEpochSecond);
    }
  }

  /** 송금 preview target 계좌 요약. 공개 사용자 응답은 계좌번호 확인값만 노출합니다. */
  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record TransferPreviewAccountResponse(
      Long accountId,
      String maskedAccountNumber,
      String displayName,
      String accountStatus,
      String currencyCode) {

    static TransferPreviewAccountResponse from(AccountSummary summary, boolean exposeInternal) {
      return new TransferPreviewAccountResponse(
          exposeInternal ? summary.accountId() : null,
          maskAccountNumber(summary.accountNumber()),
          exposeInternal ? summary.displayName() : null,
          exposeInternal ? summary.accountStatus() : null,
          exposeInternal ? summary.currencyCode() : null);
    }

    private static String maskAccountNumber(String accountNumber) {
      if (accountNumber.length() <= 4) {
        return "****";
      }
      return "********" + accountNumber.substring(accountNumber.length() - 4);
    }
  }

  /** 송금 실행 전 수취인/수수료/한도 확인 응답 */
  public record TransferPreviewResponse(
      long sourceAccountId,
      TransferPreviewAccountResponse targetAccount,
      long amountMinor,
      String currencyCode,
      long feeMinor,
      String feePolicy,
      long totalDebitMinor,
      long singleTransferLimitMinor,
      long dailyTransferLimitMinor,
      long dailyUsedMinor,
      long dailyRemainingMinor,
      boolean allowed,
      String blockedReason,
      boolean otpRequired) {}

  /** 송금 요청 body */
  public record TransferRequest(
      @Positive(message = "sourceAccountId must be positive") long sourceAccountId,
      @Positive(message = "targetAccountId must be positive") Long targetAccountId,
      @Size(max = 20, message = "targetAccountNumber must be 20 characters or less") String targetAccountNumber,
      @Positive(message = "amountMinor must be positive") long amountMinor,
      @NotBlank(message = "currencyCode is required") @Pattern(
              regexp = "^[A-Z]{3}$",
              message = "currencyCode must be a 3-letter uppercase code")
          String currencyCode,
      @NotBlank(message = "summary is required") @Size(max = 120, message = "summary must be 120 characters or less") String summary,
      @Size(max = 12, message = "totpCode must be 12 characters or less") String totpCode) {}

  /** 송금 reversal 요청 body */
  public record TransferReversalRequest(
      @Positive(message = "sourceAccountId must be positive") long sourceAccountId,
      @Positive(message = "amountMinor must be positive") Long amountMinor,
      @NotNull(message = "reversalReason is required") TransferReversalReason reversalReason,
      @NotBlank(message = "summary is required") @Size(max = 120, message = "summary must be 120 characters or less") String summary,
      @Size(max = 12, message = "totpCode must be 12 characters or less") String totpCode) {}

  /** 송금 완료 응답 */
  public record TransferResponse(
      String transactionReference,
      long sourceAccountId,
      long targetAccountId,
      long amountMinor,
      String currencyCode,
      long availableBalanceAfterMinor,
      java.time.Instant bookedAt,
      String status) {

    static TransferResponse from(TransferResult result) {
      return new TransferResponse(
          result.transactionReference(),
          result.sourceAccountId(),
          result.targetAccountId(),
          result.amountMinor(),
          result.currencyCode(),
          result.availableBalanceAfterMinor(),
          result.bookedAt(),
          result.status());
    }
  }

  /** 송금 reversal 완료 응답 */
  public record TransferReversalResponse(
      String originalTransactionReference,
      String reversalTransactionReference,
      long sourceAccountId,
      long targetAccountId,
      long amountMinor,
      String currencyCode,
      long availableBalanceAfterMinor,
      Instant bookedAt,
      String status) {

    static TransferReversalResponse from(TransferReversalResult result) {
      return new TransferReversalResponse(
          result.originalTransactionReference(),
          result.reversalTransactionReference(),
          result.sourceAccountId(),
          result.targetAccountId(),
          result.amountMinor(),
          result.currencyCode(),
          result.availableBalanceAfterMinor(),
          result.bookedAt(),
          result.status());
    }
  }
}
