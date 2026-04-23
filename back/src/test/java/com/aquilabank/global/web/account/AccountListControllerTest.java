package com.aquilabank.global.web.account;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.usecase.AccountListQueryUseCase;
import com.aquilabank.domain.account.usecase.AccountSummaryQueryUseCase;
import com.aquilabank.domain.auth.exception.AccountAccessDeniedException;
import com.aquilabank.global.security.BootstrapHeaderAuthenticationFilter;
import com.aquilabank.global.web.ApiExceptionHandler;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipalArgumentResolver;
import com.aquilabank.global.web.security.RequestAccountAuthorizationService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountListControllerTest {

  private AccountListQueryUseCase accountListQueryUseCase;
  private AccountSummaryQueryUseCase accountSummaryQueryUseCase;
  private RequestAccountAuthorizationService requestAccountAuthorizationService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    accountListQueryUseCase = mock(AccountListQueryUseCase.class);
    accountSummaryQueryUseCase = mock(AccountSummaryQueryUseCase.class);
    requestAccountAuthorizationService = mock(RequestAccountAuthorizationService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new AccountListController(
                    accountListQueryUseCase,
                    accountSummaryQueryUseCase,
                    requestAccountAuthorizationService))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new BootstrapHeaderAuthenticationFilter("X-Account-Id", "X-Subject"))
            .setCustomArgumentResolvers(new CurrentAuthenticatedPrincipalArgumentResolver())
            .build();
  }

  @Test
  void returnsSingleAccountForBootstrapPrincipal() throws Exception {
    when(requestAccountAuthorizationService.resolveReadableAccountId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(101L)))
        .thenReturn(101L);
    when(accountSummaryQueryUseCase.getByAccountId(101L))
        .thenReturn(
            new AccountSummary(
                101L,
                "100000000000001",
                "daily account",
                "ACTIVE",
                "KRW",
                8_000L,
                0L,
                Instant.parse("2026-04-16T09:00:00Z"),
                Instant.parse("2026-04-16T09:05:00Z")));

    mockMvc
        .perform(
            get("/api/v1/accounts")
                .header("X-Account-Id", "101")
                .header("X-Subject", "bootstrap-account"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].accountId").value(101L))
        .andExpect(jsonPath("$.items[0].accountNumber").value("100000000000001"))
        .andExpect(jsonPath("$.items[0].availableBalanceMinor").value(8_000L));

    verifyNoInteractions(accountListQueryUseCase);
    verify(requestAccountAuthorizationService)
        .resolveReadableAccountId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(101L));
  }

  @Test
  void rejectsBootstrapPrincipalWhenReadableAccountAccessIsDenied() throws Exception {
    when(requestAccountAuthorizationService.resolveReadableAccountId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(101L)))
        .thenThrow(new AccountAccessDeniedException("account access is denied"));

    mockMvc
        .perform(
            get("/api/v1/accounts")
                .header("X-Account-Id", "101")
                .header("X-Subject", "bootstrap-account"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value("account access is denied"));
  }

  @Test
  void rejectsMissingAuthenticationHeader() throws Exception {
    mockMvc.perform(get("/api/v1/accounts")).andExpect(status().isUnauthorized());
  }
}
