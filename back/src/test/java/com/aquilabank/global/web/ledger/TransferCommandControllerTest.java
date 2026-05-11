package com.aquilabank.global.web.ledger;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.usecase.AccountSummaryQueryUseCase;
import com.aquilabank.domain.ledger.model.TransferResult;
import com.aquilabank.domain.ledger.model.TransferReversalResult;
import com.aquilabank.domain.ledger.port.TransferLimitUsageReadPort;
import com.aquilabank.domain.ledger.usecase.TransferCommandUseCase;
import com.aquilabank.domain.ledger.usecase.TransferReversalUseCase;
import com.aquilabank.global.config.TransferLimitPolicyProperties;
import com.aquilabank.global.security.BootstrapHeaderAuthenticationFilter;
import com.aquilabank.global.web.ApiExceptionHandler;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipalArgumentResolver;
import com.aquilabank.global.web.security.RequestAccountAuthorizationService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TransferCommandControllerTest {

  private TransferCommandUseCase transferCommandUseCase;
  private TransferReversalUseCase transferReversalUseCase;
  private RequestAccountAuthorizationService requestAccountAuthorizationService;
  private AccountSummaryQueryUseCase accountSummaryQueryUseCase;
  private TransferLimitUsageReadPort transferLimitUsageReadPort;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    transferCommandUseCase = mock(TransferCommandUseCase.class);
    transferReversalUseCase = mock(TransferReversalUseCase.class);
    requestAccountAuthorizationService = mock(RequestAccountAuthorizationService.class);
    accountSummaryQueryUseCase = mock(AccountSummaryQueryUseCase.class);
    transferLimitUsageReadPort = mock(TransferLimitUsageReadPort.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new TransferCommandController(
                    transferCommandUseCase,
                    transferReversalUseCase,
                    requestAccountAuthorizationService,
                    accountSummaryQueryUseCase,
                    transferLimitUsageReadPort,
                    new TransferLimitPolicyProperties(2_000L, 3_000L, "Asia/Seoul"),
                    Clock.fixed(Instant.parse("2026-05-11T01:00:00Z"), ZoneOffset.UTC)))
            .addFilters(new BootstrapHeaderAuthenticationFilter("X-Account-Id", "X-Subject"))
            .setCustomArgumentResolvers(new CurrentAuthenticatedPrincipalArgumentResolver())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
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

  private ResultActions performPreview(long amountMinor, String currencyCode) throws Exception {
    return mockMvc.perform(
        get("/api/v1/transfers/preview")
            .header("X-Account-Id", "101")
            .queryParam("sourceAccountId", "101")
            .queryParam("targetAccountId", "202")
            .queryParam("amountMinor", String.valueOf(amountMinor))
            .queryParam("currencyCode", currencyCode));
  }
}
