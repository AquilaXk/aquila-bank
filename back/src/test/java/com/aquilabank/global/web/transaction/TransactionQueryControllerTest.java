package com.aquilabank.global.web.transaction;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import com.aquilabank.domain.transaction.model.TransactionDirection;
import com.aquilabank.domain.transaction.model.TransactionQuery;
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import com.aquilabank.domain.transaction.model.TransactionSummary;
import com.aquilabank.domain.transaction.usecase.TransactionQueryUseCase;
import com.aquilabank.global.security.BootstrapHeaderAuthenticationFilter;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipalArgumentResolver;
import com.aquilabank.global.web.security.RequestAccountAuthorizationService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TransactionQueryControllerTest {

  private TransactionQueryUseCase transactionQueryUseCase;
  private RequestAccountAuthorizationService requestAccountAuthorizationService;
  private SimpleMeterRegistry meterRegistry;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    transactionQueryUseCase = mock(TransactionQueryUseCase.class);
    requestAccountAuthorizationService = mock(RequestAccountAuthorizationService.class);
    meterRegistry = new SimpleMeterRegistry();
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new TransactionQueryController(
                    transactionQueryUseCase,
                    requestAccountAuthorizationService,
                    new TransactionReadHotPathMetrics(meterRegistry)))
            .addFilters(new BootstrapHeaderAuthenticationFilter("X-Account-Id", "X-Subject"))
            .setCustomArgumentResolvers(new CurrentAuthenticatedPrincipalArgumentResolver())
            .build();
  }

  @Test
  void returnsCursorPage() throws Exception {
    TransactionCursor nextCursor =
        new TransactionCursor(Instant.parse("2026-04-16T09:00:00Z"), 777L);
    when(transactionQueryUseCase.getTransactions(argThat(query -> query.accountId() == 101L)))
        .thenReturn(
            new TransactionSlice(
                List.of(
                    new TransactionSummary(
                        777L,
                        101L,
                        "TX-777",
                        TransactionDirection.DEBIT,
                        TransactionStatus.BOOKED,
                        1200L,
                        8800L,
                        "KRW",
                        "card payment",
                        "STORE",
                        Instant.parse("2026-04-16T09:00:00Z"))),
                nextCursor,
                true,
                20));
    when(requestAccountAuthorizationService.resolveReadableAccountId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(101L)))
        .thenReturn(101L);

    mockMvc
        .perform(
            get("/api/v1/transactions")
                .header("X-Account-Id", "101")
                .param("accountId", "101")
                .param("from", "2026-04-01T00:00:00Z")
                .param("to", "2026-04-17T00:00:00Z")
                .param("limit", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].transactionReference").value("TX-777"))
        .andExpect(jsonPath("$.items[0].status").value("BOOKED"))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.nextCursor").isString());

    assertTimerCount("active", "authorization", "success", 1);
    assertTimerCount("active", "usecase", "success", 1);
    assertTimerCount("active", "response_mapping", "success", 1);
    assertTimerCount("active", "total", "success", 1);
  }

  @Test
  void rejectsTooLargeDateRange() throws Exception {
    when(requestAccountAuthorizationService.resolveReadableAccountId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(101L)))
        .thenReturn(101L);
    mockMvc
        .perform(
            get("/api/v1/transactions")
                .header("X-Account-Id", "101")
                .param("accountId", "101")
                .param("from", "2026-01-01T00:00:00Z")
                .param("to", "2026-03-10T00:00:00Z")
                .param("limit", "20"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void decodesIncomingCursor() throws Exception {
    TransactionCursor cursor = new TransactionCursor(Instant.parse("2026-04-16T09:00:00Z"), 777L);
    String encoded = TransactionCursorCodec.encode(cursor);
    when(transactionQueryUseCase.getTransactions(argThat(query -> cursor.equals(query.cursor()))))
        .thenReturn(new TransactionSlice(List.of(), null, false, 20));
    when(requestAccountAuthorizationService.resolveReadableAccountId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(101L)))
        .thenReturn(101L);

    mockMvc
        .perform(
            get("/api/v1/transactions")
                .header("X-Account-Id", "101")
                .param("accountId", "101")
                .param("from", "2026-04-01T00:00:00Z")
                .param("to", "2026-04-17T00:00:00Z")
                .param("limit", "20")
                .param("cursor", encoded))
        .andExpect(status().isOk());

    verify(transactionQueryUseCase)
        .getTransactions(argThat(query -> cursor.equals(query.cursor())));
  }

  @Test
  void passesExpandedFiltersIntoQuery() throws Exception {
    when(transactionQueryUseCase.getTransactions(argThat(this::matchesExpandedFilters)))
        .thenReturn(new TransactionSlice(List.of(), null, false, 20));
    when(requestAccountAuthorizationService.resolveReadableAccountId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(101L)))
        .thenReturn(101L);

    mockMvc
        .perform(
            get("/api/v1/transactions")
                .header("X-Account-Id", "101")
                .param("accountId", "101")
                .param("from", "2026-04-01T00:00:00Z")
                .param("to", "2026-04-17T00:00:00Z")
                .param("limit", "20")
                .param("status", "BOOKED")
                .param("direction", "DEBIT")
                .param("minAmountMinor", "1000")
                .param("maxAmountMinor", "2000")
                .param("transactionReference", "TX-777"))
        .andExpect(status().isOk());

    verify(transactionQueryUseCase).getTransactions(argThat(this::matchesExpandedFilters));
  }

  @Test
  void rejectsInvalidAmountRange() throws Exception {
    when(requestAccountAuthorizationService.resolveReadableAccountId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(101L)))
        .thenReturn(101L);

    mockMvc
        .perform(
            get("/api/v1/transactions")
                .header("X-Account-Id", "101")
                .param("accountId", "101")
                .param("from", "2026-04-01T00:00:00Z")
                .param("to", "2026-04-17T00:00:00Z")
                .param("minAmountMinor", "2000")
                .param("maxAmountMinor", "1000"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsMissingAuthenticationHeader() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/transactions")
                .param("accountId", "101")
                .param("from", "2026-04-01T00:00:00Z")
                .param("to", "2026-04-17T00:00:00Z")
                .param("limit", "20"))
        .andExpect(status().isUnauthorized());
  }

  private boolean matchesExpandedFilters(TransactionQuery query) {
    return query.accountId() == 101L
        && query.status() == TransactionStatus.BOOKED
        && query.direction() == TransactionDirection.DEBIT
        && Long.valueOf(1000L).equals(query.minAmountMinor())
        && Long.valueOf(2000L).equals(query.maxAmountMinor())
        && "TX-777".equals(query.transactionReference());
  }

  private void assertTimerCount(String endpoint, String stage, String outcome, long expected) {
    org.assertj.core.api.Assertions.assertThat(
            meterRegistry
                .find("aquila.transaction.read.http.stage")
                .tag("endpoint", endpoint)
                .tag("stage", stage)
                .tag("outcome", outcome)
                .timer())
        .isNotNull()
        .extracting(timer -> timer.count())
        .isEqualTo(expected);
  }
}
