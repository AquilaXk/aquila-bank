package com.aquilabank.global.web.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.usecase.AccountSummaryQueryUseCase;
import com.aquilabank.domain.auth.exception.AccountAccessDeniedException;
import com.aquilabank.domain.auth.model.TotpOperationVerifyCommand;
import com.aquilabank.domain.auth.usecase.TotpOperationRequirementUseCase;
import com.aquilabank.domain.auth.usecase.TotpOperationVerifyUseCase;
import com.aquilabank.domain.ledger.model.TransferLimitPolicy;
import com.aquilabank.domain.ledger.model.TransferResult;
import com.aquilabank.domain.ledger.model.TransferReversalResult;
import com.aquilabank.domain.ledger.port.TransferLimitUsageReadPort;
import com.aquilabank.domain.ledger.usecase.TransferCommandUseCase;
import com.aquilabank.domain.ledger.usecase.TransferLimitPolicyUseCase;
import com.aquilabank.domain.ledger.usecase.TransferReversalUseCase;
import com.aquilabank.global.config.TransferLimitPolicyProperties;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.security.BootstrapHeaderAuthenticationFilter;
import com.aquilabank.global.web.ApiExceptionHandler;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipalArgumentResolver;
import com.aquilabank.global.web.security.RequestAccountAuthorizationService;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.ConcurrentMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TransferCommandControllerTest {

  private TransferCommandUseCase transferCommandUseCase;
  private TransferReversalUseCase transferReversalUseCase;
  private RequestAccountAuthorizationService requestAccountAuthorizationService;
  private AccountSummaryQueryUseCase accountSummaryQueryUseCase;
  private TransferLimitUsageReadPort transferLimitUsageReadPort;
  private TransferLimitPolicyUseCase transferLimitPolicyUseCase;
  private TotpOperationRequirementUseCase totpOperationRequirementUseCase;
  private TotpOperationVerifyUseCase totpOperationVerifyUseCase;
  private TransferCommandController controller;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    transferCommandUseCase = mock(TransferCommandUseCase.class);
    transferReversalUseCase = mock(TransferReversalUseCase.class);
    requestAccountAuthorizationService = mock(RequestAccountAuthorizationService.class);
    accountSummaryQueryUseCase = mock(AccountSummaryQueryUseCase.class);
    transferLimitUsageReadPort = mock(TransferLimitUsageReadPort.class);
    transferLimitPolicyUseCase = mock(TransferLimitPolicyUseCase.class);
    totpOperationRequirementUseCase = mock(TotpOperationRequirementUseCase.class);
    totpOperationVerifyUseCase = mock(TotpOperationVerifyUseCase.class);
    when(transferLimitPolicyUseCase.resolvePolicy(101L))
        .thenReturn(new TransferLimitPolicy(2_000L, 3_000L));
    controller =
        new TransferCommandController(
            transferCommandUseCase,
            transferReversalUseCase,
            requestAccountAuthorizationService,
            accountSummaryQueryUseCase,
            transferLimitUsageReadPort,
            transferLimitPolicyUseCase,
            totpOperationRequirementUseCase,
            totpOperationVerifyUseCase,
            new TransferLimitPolicyProperties(2_000L, 3_000L, "Asia/Seoul"),
            Clock.fixed(Instant.parse("2026-05-11T01:00:00Z"), ZoneOffset.UTC));
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .addFilters(new BootstrapHeaderAuthenticationFilter("X-Account-Id", "X-Subject"))
            .setCustomArgumentResolvers(new CurrentAuthenticatedPrincipalArgumentResolver())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void previewsTransferRecipientFeeAndLimitWithoutWritingLedger() throws Exception {
    stubPreview(
        account(101L, "111122223333", "생활비 계좌", "ACTIVE", 10_000L),
        account(202L, "999900001234", "홍길동", "ACTIVE", 0L),
        1_000L);

    performPreview(1_500L, "KRW")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sourceAccountId").value(101))
        .andExpect(jsonPath("$.targetAccount.accountId").value(202))
        .andExpect(jsonPath("$.feeMinor").value(0))
        .andExpect(jsonPath("$.singleTransferLimitMinor").value(2000))
        .andExpect(jsonPath("$.dailyTransferLimitMinor").value(3000))
        .andExpect(jsonPath("$.dailyUsedMinor").value(1000))
        .andExpect(jsonPath("$.allowed").value(true))
        .andExpect(jsonPath("$.otpRequired").value(true));
  }

  @Test
  void previewsOperationOtpRequirementForJwtUser() throws Exception {
    authenticateUser(7L);
    when(totpOperationRequirementUseCase.requiresVerification(7L)).thenReturn(true);
    stubUserPreview(
        account(101L, "111122223333", "생활비 계좌", "ACTIVE", 10_000L),
        account(202L, "999900001234", "홍길동", "ACTIVE", 0L),
        1_000L);

    performUserPreview(1_500L, "KRW")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.targetAccount.accountId").doesNotExist())
        .andExpect(jsonPath("$.targetAccount.displayName").doesNotExist())
        .andExpect(jsonPath("$.targetAccount.accountStatus").doesNotExist())
        .andExpect(jsonPath("$.targetAccount.currencyCode").doesNotExist())
        .andExpect(jsonPath("$.otpRequired").value(true));
  }

  @Test
  void rejectsUserPreviewWithoutTargetAccountNumber() throws Exception {
    authenticateUser(7L);

    mockMvc
        .perform(
            get("/api/v1/transfers/preview")
                .queryParam("sourceAccountId", "101")
                .queryParam("targetAccountId", "202")
                .queryParam("amountMinor", "1500")
                .queryParam("currencyCode", "KRW"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("targetAccountNumber is required"));
  }

  @Test
  void throttlesUserRecipientPreviewBeforeRepeatedAccountNumberLookup() throws Exception {
    authenticateUser(7L);
    when(totpOperationRequirementUseCase.requiresVerification(7L)).thenReturn(false);
    stubUserPreview(
        account(101L, "111122223333", "생활비 계좌", "ACTIVE", 10_000L),
        account(202L, "999900001234", "홍길동", "ACTIVE", 0L),
        0L);

    for (int i = 0; i < 20; i++) {
      performUserPreview(1_000L, "KRW").andExpect(status().isOk());
    }

    performUserPreview(1_000L, "KRW")
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.message").value("too many recipient preview requests"));

    verify(accountSummaryQueryUseCase, times(20)).getByAccountNumber("999900001234");
  }

  @Test
  void prunesExpiredRecipientPreviewThrottleWindowsWhenTrackedUsersExceedCap() throws Exception {
    authenticateUser(7L);
    when(totpOperationRequirementUseCase.requiresVerification(7L)).thenReturn(false);
    stubUserPreview(
        account(101L, "111122223333", "생활비 계좌", "ACTIVE", 10_000L),
        account(202L, "999900001234", "홍길동", "ACTIVE", 0L),
        0L);
    ConcurrentMap<Long, Object> windows = seedExpiredRecipientPreviewThrottleWindows(10_001);

    performUserPreview(1_000L, "KRW").andExpect(status().isOk());

    assertThat(windows).containsOnlyKeys(7L);
  }

  @Test
  void rejectsBootstrapPreviewWithoutTargetAccountId() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/transfers/preview")
                .header("X-Account-Id", "101")
                .queryParam("sourceAccountId", "101")
                .queryParam("amountMinor", "1500")
                .queryParam("currencyCode", "KRW"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("targetAccountId is required"));
  }

  @Test
  void previewsCurrencyMismatchAsBlocked() throws Exception {
    stubPreview(
        account(101L, "111122223333", "생활비 계좌", "ACTIVE", "KRW", 10_000L),
        account(202L, "999900001234", "외화 계좌", "ACTIVE", "USD", 0L),
        0L);

    performPreview(1_000L, "KRW")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allowed").value(false))
        .andExpect(jsonPath("$.blockedReason").value("CURRENCY_MISMATCH"));
  }

  @Test
  void previewsInactiveRecipientAsBlockedAndMasksShortAccountNumber() throws Exception {
    stubPreview(
        account(101L, "111122223333", "생활비 계좌", "ACTIVE", 10_000L),
        account(202L, "123", "휴면 계좌", "SUSPENDED", 0L),
        0L);

    performPreview(1_000L, "KRW")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.targetAccount.maskedAccountNumber").value("****"))
        .andExpect(jsonPath("$.allowed").value(false))
        .andExpect(jsonPath("$.blockedReason").value("TARGET_ACCOUNT_NOT_ACTIVE"));
  }

  @Test
  void previewsSingleTransferLimitExceededAsBlocked() throws Exception {
    stubPreview(
        account(101L, "111122223333", "생활비 계좌", "ACTIVE", 10_000L),
        account(202L, "999900001234", "홍길동", "ACTIVE", 0L),
        0L);

    performPreview(2_001L, "KRW")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allowed").value(false))
        .andExpect(jsonPath("$.blockedReason").value("SINGLE_LIMIT_EXCEEDED"));
  }

  @Test
  void previewsDailyTransferLimitExceededAsBlocked() throws Exception {
    stubPreview(
        account(101L, "111122223333", "생활비 계좌", "ACTIVE", 10_000L),
        account(202L, "999900001234", "홍길동", "ACTIVE", 0L),
        2_000L);

    performPreview(1_500L, "KRW")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dailyRemainingMinor").value(1_000))
        .andExpect(jsonPath("$.allowed").value(false))
        .andExpect(jsonPath("$.blockedReason").value("DAILY_LIMIT_EXCEEDED"));
  }

  @Test
  void previewsEffectiveAccountTransferLimitOverride() throws Exception {
    when(transferLimitPolicyUseCase.resolvePolicy(101L))
        .thenReturn(new TransferLimitPolicy(5_000L, 10_000L));
    stubPreview(
        account(101L, "111122223333", "생활비 계좌", "ACTIVE", 10_000L),
        account(202L, "999900001234", "홍길동", "ACTIVE", 0L),
        4_000L);

    performPreview(3_000L, "KRW")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.singleTransferLimitMinor").value(5000))
        .andExpect(jsonPath("$.dailyTransferLimitMinor").value(10000))
        .andExpect(jsonPath("$.dailyRemainingMinor").value(6000))
        .andExpect(jsonPath("$.allowed").value(true));
  }

  @Test
  void previewsInsufficientBalanceAsBlocked() throws Exception {
    stubPreview(
        account(101L, "111122223333", "생활비 계좌", "ACTIVE", 1_000L),
        account(202L, "999900001234", "홍길동", "ACTIVE", 0L),
        0L);

    performPreview(1_500L, "KRW")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.allowed").value(false))
        .andExpect(jsonPath("$.blockedReason").value("INSUFFICIENT_BALANCE"));
  }

  @Test
  void transfersUsingAuthenticatedAccountAndIdempotencyKey() throws Exception {
    when(transferCommandUseCase.transfer(argThat(command -> command.sourceAccountId() == 101L)))
        .thenReturn(
            new TransferResult(
                "TRX-1",
                101L,
                202L,
                1500L,
                "KRW",
                8500L,
                java.time.Instant.parse("2026-04-16T10:00:00Z"),
                "BOOKED"));
    when(requestAccountAuthorizationService.resolveTransferSourceAccountId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(101L)))
        .thenReturn(101L);

    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("X-Account-Id", "101")
                .header("Idempotency-Key", "transfer-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": 101,
                      "targetAccountId": 202,
                      "amountMinor": 1500,
                      "currencyCode": "KRW",
                      "summary": "rent"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionReference").value("TRX-1"))
        .andExpect(jsonPath("$.sourceAccountId").value(101))
        .andExpect(jsonPath("$.availableBalanceAfterMinor").value(8500));

    verify(transferCommandUseCase)
        .transfer(argThat(command -> "transfer-001".equals(command.idempotencyKey())));
  }

  @Test
  void rejectsUserTransferWhenOperationTotpIsRequiredAndMissing() throws Exception {
    authenticateUser(7L);
    when(totpOperationRequirementUseCase.requiresVerification(7L)).thenReturn(true);
    when(requestAccountAuthorizationService.resolveTransferSourceAccountId(
            org.mockito.ArgumentMatchers.any(), eq(101L)))
        .thenReturn(101L);
    when(accountSummaryQueryUseCase.getByAccountNumber("999900001234"))
        .thenReturn(account(202L, "999900001234", "홍길동", "ACTIVE", 0L));

    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("Idempotency-Key", "transfer-user-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": 101,
                      "targetAccountNumber": "999900001234",
                      "amountMinor": 1500,
                      "currencyCode": "KRW",
                      "summary": "rent"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("totpCode is required"));

    verifyNoInteractions(totpOperationVerifyUseCase);
    verifyNoInteractions(transferCommandUseCase);
  }

  @Test
  void verifiesUserTransferOperationTotpAfterAccountResolutionBeforeCommandExecution()
      throws Exception {
    authenticateUser(7L);
    when(totpOperationRequirementUseCase.requiresVerification(7L)).thenReturn(true);
    when(accountSummaryQueryUseCase.getByAccountNumber("999900001234"))
        .thenReturn(account(202L, "999900001234", "홍길동", "ACTIVE", 0L));
    when(transferCommandUseCase.transfer(argThat(command -> command.sourceAccountId() == 101L)))
        .thenReturn(
            new TransferResult(
                "TRX-USER-1",
                101L,
                202L,
                1500L,
                "KRW",
                8500L,
                java.time.Instant.parse("2026-04-16T10:00:00Z"),
                "BOOKED"));
    when(requestAccountAuthorizationService.resolveTransferSourceAccountId(
            org.mockito.ArgumentMatchers.any(), eq(101L)))
        .thenReturn(101L);

    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("Idempotency-Key", "transfer-user-002")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": 101,
                      "targetAccountNumber": "999900001234",
                      "amountMinor": 1500,
                      "currencyCode": "KRW",
                      "summary": "rent",
                      "totpCode": "123456"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionReference").value("TRX-USER-1"));

    InOrder inOrder =
        inOrder(
            requestAccountAuthorizationService,
            accountSummaryQueryUseCase,
            totpOperationVerifyUseCase,
            transferCommandUseCase);
    inOrder
        .verify(requestAccountAuthorizationService)
        .resolveTransferSourceAccountId(org.mockito.ArgumentMatchers.any(), eq(101L));
    inOrder.verify(accountSummaryQueryUseCase).getByAccountNumber("999900001234");
    inOrder
        .verify(totpOperationVerifyUseCase)
        .verify(eq(new TotpOperationVerifyCommand(7L, "123456")));
    inOrder
        .verify(transferCommandUseCase)
        .transfer(
            argThat(
                command ->
                    "transfer-user-002".equals(command.idempotencyKey())
                        && command.targetAccountId() == 202L));
  }

  @Test
  void rejectsUserTransferWithoutConsumingTotpWhenSourceAccountIsDenied() throws Exception {
    authenticateUser(7L);
    when(requestAccountAuthorizationService.resolveTransferSourceAccountId(
            org.mockito.ArgumentMatchers.any(), eq(101L)))
        .thenThrow(new AccountAccessDeniedException("account access is denied"));

    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("Idempotency-Key", "transfer-user-denied")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": 101,
                      "targetAccountNumber": "999900001234",
                      "amountMinor": 1500,
                      "currencyCode": "KRW",
                      "summary": "rent",
                      "totpCode": "123456"
                    }
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));

    verifyNoInteractions(totpOperationVerifyUseCase);
    verifyNoInteractions(transferCommandUseCase);
  }

  @Test
  void rejectsUserTransferWithoutTargetAccountNumber() throws Exception {
    authenticateUser(7L);
    when(totpOperationRequirementUseCase.requiresVerification(7L)).thenReturn(false);

    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("Idempotency-Key", "transfer-user-003")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": 101,
                      "targetAccountId": 202,
                      "amountMinor": 1500,
                      "currencyCode": "KRW",
                      "summary": "rent"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("targetAccountNumber is required"));

    verifyNoInteractions(transferCommandUseCase);
  }

  @Test
  void rejectsBootstrapTransferWithoutTargetAccountId() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("X-Account-Id", "101")
                .header("Idempotency-Key", "transfer-bootstrap-missing-target")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": 101,
                      "amountMinor": 1500,
                      "currencyCode": "KRW",
                      "summary": "rent"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("targetAccountId is required"));

    verifyNoInteractions(transferCommandUseCase);
  }

  @Test
  void reversesTransferUsingAuthenticatedAccountAndIdempotencyKey() throws Exception {
    when(transferReversalUseCase.reverse(
            argThat(command -> "TRX-1".equals(command.originalTransactionReference()))))
        .thenReturn(
            new TransferReversalResult(
                "TRX-1",
                "TRX-2",
                101L,
                202L,
                1500L,
                "KRW",
                10_000L,
                java.time.Instant.parse("2026-04-16T10:10:00Z"),
                "REVERSED"));
    when(requestAccountAuthorizationService.resolveTransferSourceAccountId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(101L)))
        .thenReturn(101L);

    mockMvc
        .perform(
            post("/api/v1/transfers/TRX-1/reversal")
                .header("X-Account-Id", "101")
                .header("Idempotency-Key", "reversal-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": 101,
                      "amountMinor": 700,
                      "reversalReason": "CANCEL",
                      "summary": "cancel transfer"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.originalTransactionReference").value("TRX-1"))
        .andExpect(jsonPath("$.reversalTransactionReference").value("TRX-2"))
        .andExpect(jsonPath("$.availableBalanceAfterMinor").value(10000));

    verify(transferReversalUseCase)
        .reverse(
            argThat(
                command ->
                    "reversal-001".equals(command.idempotencyKey())
                        && Long.valueOf(700L).equals(command.amountMinor())));
  }

  @Test
  void verifiesUserReversalOperationTotpAfterAccountAuthorizationBeforeCommandExecution()
      throws Exception {
    authenticateUser(7L);
    when(totpOperationRequirementUseCase.requiresVerification(7L)).thenReturn(true);
    when(transferReversalUseCase.reverse(
            argThat(command -> "TRX-1".equals(command.originalTransactionReference()))))
        .thenReturn(
            new TransferReversalResult(
                "TRX-1",
                "TRX-2",
                101L,
                202L,
                1500L,
                "KRW",
                10_000L,
                java.time.Instant.parse("2026-04-16T10:10:00Z"),
                "REVERSED"));
    when(requestAccountAuthorizationService.resolveTransferSourceAccountId(
            org.mockito.ArgumentMatchers.any(), eq(101L)))
        .thenReturn(101L);

    mockMvc
        .perform(
            post("/api/v1/transfers/TRX-1/reversal")
                .header("Idempotency-Key", "reversal-user-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": 101,
                      "amountMinor": 700,
                      "reversalReason": "CANCEL",
                      "summary": "cancel transfer",
                      "totpCode": "123456"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.reversalTransactionReference").value("TRX-2"));

    InOrder inOrder =
        inOrder(
            requestAccountAuthorizationService,
            totpOperationVerifyUseCase,
            transferReversalUseCase);
    inOrder
        .verify(requestAccountAuthorizationService)
        .resolveTransferSourceAccountId(org.mockito.ArgumentMatchers.any(), eq(101L));
    inOrder
        .verify(totpOperationVerifyUseCase)
        .verify(eq(new TotpOperationVerifyCommand(7L, "123456")));
    inOrder
        .verify(transferReversalUseCase)
        .reverse(argThat(command -> "reversal-user-001".equals(command.idempotencyKey())));
  }

  @Test
  void rejectsUserReversalWithoutConsumingTotpWhenSourceAccountIsDenied() throws Exception {
    authenticateUser(7L);
    when(requestAccountAuthorizationService.resolveTransferSourceAccountId(
            org.mockito.ArgumentMatchers.any(), eq(101L)))
        .thenThrow(new AccountAccessDeniedException("account access is denied"));

    mockMvc
        .perform(
            post("/api/v1/transfers/TRX-1/reversal")
                .header("Idempotency-Key", "reversal-user-denied")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": 101,
                      "amountMinor": 700,
                      "reversalReason": "CANCEL",
                      "summary": "cancel transfer",
                      "totpCode": "123456"
                    }
                    """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));

    verifyNoInteractions(totpOperationVerifyUseCase);
    verifyNoInteractions(transferReversalUseCase);
  }

  @Test
  void rejectsMissingAuthenticationHeader() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("Idempotency-Key", "transfer-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": 101,
                      "targetAccountId": 202,
                      "amountMinor": 1500,
                      "currencyCode": "KRW",
                      "summary": "rent"
                    }
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void rejectsInvalidBody() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("X-Account-Id", "101")
                .header("Idempotency-Key", "transfer-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": 0,
                      "targetAccountId": 101,
                      "amountMinor": 0,
                      "currencyCode": "krw",
                      "summary": ""
                    }
                    """))
        .andExpect(status().isBadRequest());
  }

  private static AccountSummary account(
      long accountId,
      String accountNumber,
      String displayName,
      String status,
      long availableBalanceMinor) {
    return account(accountId, accountNumber, displayName, status, "KRW", availableBalanceMinor);
  }

  private static AccountSummary account(
      long accountId,
      String accountNumber,
      String displayName,
      String status,
      String currencyCode,
      long availableBalanceMinor) {
    return new AccountSummary(
        accountId,
        accountNumber,
        displayName,
        status,
        currencyCode,
        availableBalanceMinor,
        0L,
        Instant.parse("2026-05-11T00:00:00Z"),
        Instant.parse("2026-05-11T00:00:00Z"));
  }

  private void stubPreview(
      AccountSummary sourceAccount, AccountSummary targetAccount, long dailyUsedMinor) {
    when(requestAccountAuthorizationService.resolveTransferSourceAccountId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(101L)))
        .thenReturn(101L);
    when(accountSummaryQueryUseCase.getByAccountId(101L)).thenReturn(sourceAccount);
    when(accountSummaryQueryUseCase.getByAccountId(202L)).thenReturn(targetAccount);
    when(transferLimitUsageReadPort.sumBookedDebitAmountMinor(
            org.mockito.ArgumentMatchers.eq(101L),
            org.mockito.ArgumentMatchers.eq("KRW"),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(dailyUsedMinor);
  }

  private void stubUserPreview(
      AccountSummary sourceAccount, AccountSummary targetAccount, long dailyUsedMinor) {
    when(requestAccountAuthorizationService.resolveTransferSourceAccountId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(101L)))
        .thenReturn(101L);
    when(accountSummaryQueryUseCase.getByAccountId(101L)).thenReturn(sourceAccount);
    when(accountSummaryQueryUseCase.getByAccountNumber(targetAccount.accountNumber()))
        .thenReturn(targetAccount);
    when(transferLimitUsageReadPort.sumBookedDebitAmountMinor(
            org.mockito.ArgumentMatchers.eq(101L),
            org.mockito.ArgumentMatchers.eq("KRW"),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(dailyUsedMinor);
  }

  private ResultActions performPreview(long amountMinor, String currencyCode) throws Exception {
    return mockMvc.perform(
        get("/api/v1/transfers/preview")
            .header("X-Account-Id", "101")
            .queryParam("sourceAccountId", "101")
            .queryParam("targetAccountId", "202")
            .queryParam("amountMinor", String.valueOf(amountMinor))
            .queryParam("currencyCode", currencyCode));
  }

  private ResultActions performUserPreview(long amountMinor, String currencyCode) throws Exception {
    return mockMvc.perform(
        get("/api/v1/transfers/preview")
            .queryParam("sourceAccountId", "101")
            .queryParam("targetAccountNumber", "999900001234")
            .queryParam("amountMinor", String.valueOf(amountMinor))
            .queryParam("currencyCode", currencyCode));
  }

  @SuppressWarnings("unchecked")
  private ConcurrentMap<Long, Object> seedExpiredRecipientPreviewThrottleWindows(int count)
      throws Exception {
    Field windowsField =
        TransferCommandController.class.getDeclaredField("recipientPreviewThrottleWindows");
    windowsField.setAccessible(true);
    ConcurrentMap<Long, Object> windows =
        (ConcurrentMap<Long, Object>) windowsField.get(controller);
    Class<?> windowType =
        Class.forName(
            "com.aquilabank.global.web.ledger.TransferCommandController$RecipientPreviewThrottleWindow");
    Constructor<?> constructor = windowType.getDeclaredConstructor(long.class, int.class);
    constructor.setAccessible(true);
    Object expiredWindow =
        constructor.newInstance(Instant.parse("2026-05-11T00:58:59Z").getEpochSecond(), 1);
    for (long userId = 10_000L; userId < 10_000L + count; userId++) {
      windows.put(userId, expiredWindow);
    }
    return windows;
  }

  private void authenticateUser(long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUserPrincipal(userId, "tester"), null, List.of()));
  }
}
