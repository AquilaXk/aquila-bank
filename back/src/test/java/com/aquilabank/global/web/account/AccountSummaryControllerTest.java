package com.aquilabank.global.web.account;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.account.exception.AccountSummaryNotFoundException;
import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.usecase.AccountSummaryQueryUseCase;
import com.aquilabank.global.security.BootstrapHeaderAuthenticationFilter;
import com.aquilabank.global.web.ApiExceptionHandler;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipalArgumentResolver;
import com.aquilabank.global.web.security.RequestAccountAuthorizationService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountSummaryControllerTest {

  private AccountSummaryQueryUseCase accountSummaryQueryUseCase;
  private RequestAccountAuthorizationService requestAccountAuthorizationService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    accountSummaryQueryUseCase = mock(AccountSummaryQueryUseCase.class);
    requestAccountAuthorizationService = mock(RequestAccountAuthorizationService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new AccountSummaryController(
                    accountSummaryQueryUseCase, requestAccountAuthorizationService))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new BootstrapHeaderAuthenticationFilter("X-Account-Id", "X-Subject"))
            .setCustomArgumentResolvers(new CurrentAuthenticatedPrincipalArgumentResolver())
            .build();
  }

  @Test
  void returnsAccountSummary() throws Exception {
    when(requestAccountAuthorizationService.resolveReadableAccountId(any(), eq(101L)))
        .thenReturn(101L);
    when(accountSummaryQueryUseCase.getByAccountId(101L))
        .thenReturn(
            new AccountSummary(
                101L,
                "100000000000001",
                "daily account",
                "ACTIVE",
                "KRW",
                8000L,
                0L,
                Instant.parse("2026-04-16T09:00:00Z"),
                Instant.parse("2026-04-16T09:05:00Z")));

    mockMvc
        .perform(get("/api/v1/accounts/101").header("X-Account-Id", "101"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(101L))
        .andExpect(jsonPath("$.accountNumber").value("100000000000001"))
        .andExpect(jsonPath("$.availableBalanceMinor").value(8000L))
        .andExpect(jsonPath("$.pendingBalanceMinor").value(0L));
  }

  @Test
  void returnsNotFoundWhenAccountSummaryIsMissing() throws Exception {
    when(requestAccountAuthorizationService.resolveReadableAccountId(any(), eq(101L)))
        .thenReturn(101L);
    when(accountSummaryQueryUseCase.getByAccountId(101L))
        .thenThrow(new AccountSummaryNotFoundException("account summary is not found"));

    mockMvc
        .perform(get("/api/v1/accounts/101").header("X-Account-Id", "101"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("account summary is not found"));
  }

  @Test
  void rejectsMissingAuthenticationHeader() throws Exception {
    mockMvc.perform(get("/api/v1/accounts/101")).andExpect(status().isUnauthorized());
  }
}
