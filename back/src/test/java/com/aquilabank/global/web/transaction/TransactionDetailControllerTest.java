package com.aquilabank.global.web.transaction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.transaction.exception.TransactionDetailNotFoundException;
import com.aquilabank.domain.transaction.model.TransactionDetail;
import com.aquilabank.domain.transaction.model.TransactionDirection;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import com.aquilabank.domain.transaction.usecase.TransactionDetailQueryUseCase;
import com.aquilabank.global.security.BootstrapHeaderAuthenticationFilter;
import com.aquilabank.global.web.ApiExceptionHandler;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipalArgumentResolver;
import com.aquilabank.global.web.security.RequestAccountAuthorizationService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TransactionDetailControllerTest {

  private TransactionDetailQueryUseCase transactionDetailQueryUseCase;
  private RequestAccountAuthorizationService requestAccountAuthorizationService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    transactionDetailQueryUseCase = mock(TransactionDetailQueryUseCase.class);
    requestAccountAuthorizationService = mock(RequestAccountAuthorizationService.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new TransactionDetailController(
                    transactionDetailQueryUseCase, requestAccountAuthorizationService))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new BootstrapHeaderAuthenticationFilter("X-Account-Id", "X-Subject"))
            .setCustomArgumentResolvers(new CurrentAuthenticatedPrincipalArgumentResolver())
            .build();
  }

  @Test
  void returnsTransactionDetail() throws Exception {
    when(requestAccountAuthorizationService.resolveReadableAccountId(any(), eq(101L)))
        .thenReturn(101L);
    when(transactionDetailQueryUseCase.getTransactionDetail(any()))
        .thenReturn(
            new TransactionDetail(
                101L,
                "TX-101",
                TransactionDirection.DEBIT,
                TransactionStatus.BOOKED,
                1200L,
                8800L,
                "KRW",
                "card payment",
                "STORE",
                Instant.parse("2026-04-16T09:00:00Z"),
                "ENT-101",
                TransactionStatus.BOOKED,
                Instant.parse("2026-04-16T09:00:01Z"),
                "card payment"));

    mockMvc
        .perform(
            get("/api/v1/transactions/TX-101")
                .header("X-Account-Id", "101")
                .param("accountId", "101"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.transactionReference").value("TX-101"))
        .andExpect(jsonPath("$.transactionStatus").value("BOOKED"))
        .andExpect(jsonPath("$.entryReference").value("ENT-101"));
  }

  @Test
  void returnsNotFoundWhenTransactionDetailIsMissing() throws Exception {
    when(requestAccountAuthorizationService.resolveReadableAccountId(any(), eq(101L)))
        .thenReturn(101L);
    when(transactionDetailQueryUseCase.getTransactionDetail(any()))
        .thenThrow(new TransactionDetailNotFoundException("transaction detail is not found"));

    mockMvc
        .perform(
            get("/api/v1/transactions/TX-404")
                .header("X-Account-Id", "101")
                .param("accountId", "101"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("transaction detail is not found"));
  }

  @Test
  void rejectsMissingAuthenticationHeader() throws Exception {
    mockMvc
        .perform(get("/api/v1/transactions/TX-101").param("accountId", "101"))
        .andExpect(status().isUnauthorized());
  }
}
