package com.aquilabank.global.web.ledger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.web.ApiExceptionHandler;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipalArgumentResolver;
import com.aquilabank.global.web.security.RequestAccountAuthorizationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ScheduledTransferControllerTest {

  private static final Instant NOW = Instant.parse("2026-11-21T12:00:00Z");

  private ScheduledTransferUseCase scheduledTransferUseCase;
  private RequestAccountAuthorizationService requestAccountAuthorizationService;
  private AccountSummaryQueryUseCase accountSummaryQueryUseCase;
  private RecipientPreviewThrottlePort recipientPreviewThrottlePort;
  private TotpOperationRequirementUseCase totpOperationRequirementUseCase;
  private TotpOperationVerifyUseCase totpOperationVerifyUseCase;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    scheduledTransferUseCase = mock(ScheduledTransferUseCase.class);
    requestAccountAuthorizationService = mock(RequestAccountAuthorizationService.class);
    accountSummaryQueryUseCase = mock(AccountSummaryQueryUseCase.class);
    recipientPreviewThrottlePort = mock(RecipientPreviewThrottlePort.class);
    totpOperationRequirementUseCase = mock(TotpOperationRequirementUseCase.class);
    totpOperationVerifyUseCase = mock(TotpOperationVerifyUseCase.class);
    when(recipientPreviewThrottlePort.consume(anyLong(), any(), anyInt(), anyLong()))
        .thenReturn(new RecipientPreviewThrottleDecision(true, 0L));
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new ScheduledTransferController(
                    scheduledTransferUseCase,
                    requestAccountAuthorizationService,
                    accountSummaryQueryUseCase,
                    recipientPreviewThrottlePort,
                    totpOperationRequirementUseCase,
                    totpOperationVerifyUseCase))
            .setControllerAdvice(new ApiExceptionHandler())
            .setCustomArgumentResolvers(new CurrentAuthenticatedPrincipalArgumentResolver())
            .build();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void listsCreatesAndUpdatesScheduledTransfersWithTotpCheck() throws Exception {
    authenticateUser(7L);
    AccountSummary source = account(101L, "110-111-222333", "KRW");
    AccountSummary target = account(202L, "110-444-555666", "KRW");
    ScheduledTransferRule rule =
        new ScheduledTransferRule(
            5L,
            7L,
            101L,
            "110-444-555666",
            150_000L,
            "KRW",
            "적금 이체",
            ScheduledTransferFrequency.MONTHLY,
            15,
            NOW.plusSeconds(86400),
            ScheduledTransferStatus.ACTIVE,
            NOW,
            NOW);
    ScheduledTransferRule paused =
        new ScheduledTransferRule(
            5L,
            7L,
            101L,
            "110-444-555666",
            150_000L,
            "KRW",
            "적금 이체",
            ScheduledTransferFrequency.MONTHLY,
            15,
            NOW.plusSeconds(86400),
            ScheduledTransferStatus.PAUSED,
            NOW,
            NOW);

    when(scheduledTransferUseCase.list(7L)).thenReturn(List.of(rule));
    when(requestAccountAuthorizationService.resolveTransferSourceAccountId(
            any(), org.mockito.ArgumentMatchers.eq(101L)))
        .thenReturn(101L);
    when(accountSummaryQueryUseCase.getByAccountId(101L)).thenReturn(source);
    when(accountSummaryQueryUseCase.getByAccountNumber("110-444-555666")).thenReturn(target);
    when(totpOperationRequirementUseCase.requiresVerification(7L)).thenReturn(true);
    when(scheduledTransferUseCase.create(any(CreateScheduledTransferCommand.class)))
        .thenReturn(rule);
    when(scheduledTransferUseCase.updateStatus(7L, 5L, ScheduledTransferStatus.PAUSED))
        .thenReturn(paused);

    mockMvc
        .perform(get("/api/v1/transfers/schedules"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].scheduleId").value(5L));

    mockMvc
        .perform(
            post("/api/v1/transfers/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": 101,
                      "targetAccountNumber": "110-444-555666",
                      "amountMinor": 150000,
                      "currencyCode": "KRW",
                      "summary": "적금 이체",
                      "scheduleType": "MONTHLY",
                      "scheduledDay": 15,
                      "nextExecutionAt": "2026-11-22T12:00:00Z",
                      "totpCode": "123456"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ruleStatus").value("ACTIVE"));

    verify(totpOperationVerifyUseCase).verify(new TotpOperationVerifyCommand(7L, "123456"));

    when(totpOperationRequirementUseCase.requiresVerification(7L)).thenReturn(false);
    mockMvc
        .perform(
            post("/api/v1/transfers/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": 101,
                      "targetAccountNumber": "110-444-555666",
                      "amountMinor": 150000,
                      "currencyCode": "KRW",
                      "summary": "적금 이체",
                      "scheduleType": "ONCE",
                      "scheduledDay": 15,
                      "nextExecutionAt": "2026-11-22T12:00:00Z"
                    }
                    """))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            post("/api/v1/transfers/schedules/5/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ruleStatus\":\"PAUSED\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ruleStatus").value("PAUSED"));

    when(scheduledTransferUseCase.get(7L, 5L)).thenReturn(paused);
    when(totpOperationRequirementUseCase.requiresVerification(7L)).thenReturn(true);
    when(scheduledTransferUseCase.updateStatus(7L, 5L, ScheduledTransferStatus.ACTIVE))
        .thenReturn(rule);
    mockMvc
        .perform(
            post("/api/v1/transfers/schedules/5/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ruleStatus\":\"ACTIVE\",\"totpCode\":\"654321\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.ruleStatus").value("ACTIVE"));
    verify(totpOperationVerifyUseCase).verify(new TotpOperationVerifyCommand(7L, "654321"));
  }

  @Test
  void blocksTargetLookupBeforeTotpOrWhenThrottledOrInactive() throws Exception {
    authenticateUser(7L);
    AccountSummary source = account(101L, "110-111-222333", "KRW");
    AccountSummary suspendedTarget =
        new AccountSummary(204L, "110-999-000111", "정지계좌", "SUSPENDED", "KRW", 0L, 0L, NOW, NOW);
    ScheduledTransferRule paused =
        new ScheduledTransferRule(
            5L,
            7L,
            101L,
            "110-444-555666",
            150_000L,
            "KRW",
            "적금 이체",
            ScheduledTransferFrequency.MONTHLY,
            15,
            NOW.plusSeconds(86400),
            ScheduledTransferStatus.PAUSED,
            NOW,
            NOW);

    when(requestAccountAuthorizationService.resolveTransferSourceAccountId(any(), anyLong()))
        .thenReturn(101L);
    when(accountSummaryQueryUseCase.getByAccountId(101L)).thenReturn(source);
    when(totpOperationRequirementUseCase.requiresVerification(7L)).thenReturn(true);

    mockMvc
        .perform(
            post("/api/v1/transfers/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": 101,
                      "targetAccountNumber": "110-444-555666",
                      "amountMinor": 1000,
                      "currencyCode": "KRW",
                      "summary": "미인증 열거 시도",
                      "scheduleType": "ONCE",
                      "scheduledDay": 1,
                      "nextExecutionAt": "2026-11-22T12:00:00Z"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("totpCode is required"));
    verify(accountSummaryQueryUseCase, never()).getByAccountNumber(anyString());

    doThrow(new IllegalArgumentException("invalid totp code"))
        .when(totpOperationVerifyUseCase)
        .verify(new TotpOperationVerifyCommand(7L, "000000"));
    mockMvc
        .perform(
            post("/api/v1/transfers/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": 101,
                      "targetAccountNumber": "110-444-555666",
                      "amountMinor": 1000,
                      "currencyCode": "KRW",
                      "summary": "잘못된 OTP 열거 시도",
                      "scheduleType": "ONCE",
                      "scheduledDay": 1,
                      "nextExecutionAt": "2026-11-22T12:00:00Z",
                      "totpCode": "000000"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("invalid totp code"));
    verify(accountSummaryQueryUseCase, never()).getByAccountNumber(anyString());

    when(totpOperationRequirementUseCase.requiresVerification(7L)).thenReturn(false);
    when(recipientPreviewThrottlePort.consume(anyLong(), any(), anyInt(), anyLong()))
        .thenReturn(new RecipientPreviewThrottleDecision(false, 30L));
    mockMvc
        .perform(
            post("/api/v1/transfers/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": 101,
                      "targetAccountNumber": "110-444-555666",
                      "amountMinor": 1000,
                      "currencyCode": "KRW",
                      "summary": "과도 조회",
                      "scheduleType": "ONCE",
                      "scheduledDay": 1,
                      "nextExecutionAt": "2026-11-22T12:00:00Z"
                    }
                    """))
        .andExpect(status().isTooManyRequests());
    verify(accountSummaryQueryUseCase, never()).getByAccountNumber(anyString());

    when(recipientPreviewThrottlePort.consume(anyLong(), any(), anyInt(), anyLong()))
        .thenReturn(new RecipientPreviewThrottleDecision(true, 0L));
    when(accountSummaryQueryUseCase.getByAccountNumber("110-999-000111"))
        .thenReturn(suspendedTarget);
    mockMvc
        .perform(
            post("/api/v1/transfers/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": 101,
                      "targetAccountNumber": "110-999-000111",
                      "amountMinor": 1000,
                      "currencyCode": "KRW",
                      "summary": "정지 계좌",
                      "scheduleType": "ONCE",
                      "scheduledDay": 1,
                      "nextExecutionAt": "2026-11-22T12:00:00Z"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("target account is not active"));

    when(scheduledTransferUseCase.get(7L, 5L)).thenReturn(paused);
    when(accountSummaryQueryUseCase.getByAccountId(101L))
        .thenReturn(
            new AccountSummary(101L, "110-111-222333", "정지 출금", "LOCKED", "KRW", 0L, 0L, NOW, NOW));
    mockMvc
        .perform(
            post("/api/v1/transfers/schedules/5/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ruleStatus\":\"ACTIVE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("source account is not active"));
  }

  @Test
  void rejectsSameAccountAndCurrencyMismatchOnScheduleCreation() throws Exception {
    authenticateUser(7L);
    AccountSummary source = account(101L, "110-111-222333", "KRW");
    AccountSummary usdSource = account(102L, "110-777-888999", "USD");
    AccountSummary krwTarget = account(202L, "110-444-555666", "KRW");
    AccountSummary usdTarget = account(203L, "110-000-111222", "USD");

    when(requestAccountAuthorizationService.resolveTransferSourceAccountId(
            any(), org.mockito.ArgumentMatchers.eq(101L)))
        .thenReturn(101L);
    when(requestAccountAuthorizationService.resolveTransferSourceAccountId(
            any(), org.mockito.ArgumentMatchers.eq(102L)))
        .thenReturn(102L);
    when(accountSummaryQueryUseCase.getByAccountId(101L)).thenReturn(source);
    when(accountSummaryQueryUseCase.getByAccountId(102L)).thenReturn(usdSource);
    when(accountSummaryQueryUseCase.getByAccountNumber("110-111-222333")).thenReturn(source);
    when(accountSummaryQueryUseCase.getByAccountNumber("110-444-555666")).thenReturn(krwTarget);
    when(accountSummaryQueryUseCase.getByAccountNumber("110-000-111222")).thenReturn(usdTarget);

    mockMvc
        .perform(
            post("/api/v1/transfers/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": 101,
                      "targetAccountNumber": "110-111-222333",
                      "amountMinor": 1000,
                      "currencyCode": "KRW",
                      "summary": "본인 계좌",
                      "scheduleType": "ONCE",
                      "scheduledDay": 1,
                      "nextExecutionAt": "2026-11-22T12:00:00Z"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("sourceAccountId and targetAccountId must differ"));

    mockMvc
        .perform(
            post("/api/v1/transfers/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": 102,
                      "targetAccountNumber": "110-444-555666",
                      "amountMinor": 1000,
                      "currencyCode": "KRW",
                      "summary": "출금 통화 불일치",
                      "scheduleType": "ONCE",
                      "scheduledDay": 1,
                      "nextExecutionAt": "2026-11-22T12:00:00Z"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("currencyCode mismatch"));

    mockMvc
        .perform(
            post("/api/v1/transfers/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "sourceAccountId": 101,
                      "targetAccountNumber": "110-000-111222",
                      "amountMinor": 1000,
                      "currencyCode": "KRW",
                      "summary": "입금 통화 불일치",
                      "scheduleType": "ONCE",
                      "scheduledDay": 1,
                      "nextExecutionAt": "2026-11-22T12:00:00Z"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("currencyCode mismatch"));
  }

  private static AccountSummary account(long id, String number, String currency) {
    return new AccountSummary(id, number, "계좌", "ACTIVE", currency, 1_000_000L, 0L, NOW, NOW);
  }

  private void authenticateUser(long userId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUserPrincipal(userId, "tester"), null, List.of()));
  }
}
