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
                    transactionDetailQueryUseCase,
                    requestAccountAuthorizationService,
                    new TransactionReadHotPathMetrics(
                        new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                    new TransactionReadSingleFlight(),
                    new TransactionReadAccountFairnessLimiter(2)))
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

  @Test
  void recordsDetailHotPathMetricsAndEnforcesFairnessLimiterBeforeSingleFlight() throws Exception {
    io.micrometer.core.instrument.simple.SimpleMeterRegistry meterRegistry =
        new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
    TransactionReadAccountFairnessLimiter limiter = new TransactionReadAccountFairnessLimiter(1);
    TransactionReadSingleFlight singleFlight =
        org.mockito.Mockito.spy(new TransactionReadSingleFlight());
    MockMvc measuredMockMvc =
        MockMvcBuilders.standaloneSetup(
                new TransactionDetailController(
                    transactionDetailQueryUseCase,
                    requestAccountAuthorizationService,
                    new TransactionReadHotPathMetrics(meterRegistry),
                    singleFlight,
                    limiter))
            .setControllerAdvice(new ApiExceptionHandler())
            .addFilters(new BootstrapHeaderAuthenticationFilter("X-Account-Id", "X-Subject"))
            .setCustomArgumentResolvers(new CurrentAuthenticatedPrincipalArgumentResolver())
            .build();

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

    measuredMockMvc
        .perform(
            get("/api/v1/transactions/TX-101")
                .header("X-Account-Id", "101")
                .param("accountId", "101"))
        .andExpect(status().isOk());

    for (String stage :
        java.util.List.of("authorization", "usecase", "response_mapping", "total")) {
      org.assertj.core.api.Assertions.assertThat(
              meterRegistry
                  .find("aquila.transaction.read.http.stage")
                  .tag("endpoint", "detail")
                  .tag("stage", stage)
                  .tag("outcome", "success")
                  .timer()
                  .count())
          .isEqualTo(1L);
    }

    org.mockito.Mockito.clearInvocations(singleFlight);
    java.util.concurrent.CountDownLatch enteredLimiter = new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.CountDownLatch releaseLimiter = new java.util.concurrent.CountDownLatch(1);
    Thread holder =
        new Thread(
            () ->
                limiter.execute(
                    101L,
                    () -> {
                      enteredLimiter.countDown();
                      try {
                        releaseLimiter.await();
                      } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                      }
                      return null;
                    }));
    holder.start();
    enteredLimiter.await();
    try {
      measuredMockMvc
          .perform(
              get("/api/v1/transactions/TX-101")
                  .header("X-Account-Id", "101")
                  .param("accountId", "101"))
          .andExpect(status().isTooManyRequests());
      org.mockito.Mockito.verifyNoInteractions(singleFlight);
    } finally {
      releaseLimiter.countDown();
      holder.join();
    }
  }
}
