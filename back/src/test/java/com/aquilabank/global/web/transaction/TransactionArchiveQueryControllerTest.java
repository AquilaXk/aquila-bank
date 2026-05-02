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
import com.aquilabank.domain.transaction.usecase.TransactionArchiveQueryUseCase;
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

class TransactionArchiveQueryControllerTest {

  private TransactionArchiveQueryUseCase transactionArchiveQueryUseCase;
  private RequestAccountAuthorizationService requestAccountAuthorizationService;
  private SimpleMeterRegistry meterRegistry;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    transactionArchiveQueryUseCase = mock(TransactionArchiveQueryUseCase.class);
    requestAccountAuthorizationService = mock(RequestAccountAuthorizationService.class);
    meterRegistry = new SimpleMeterRegistry();
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new TransactionArchiveQueryController(
                    transactionArchiveQueryUseCase,
                    requestAccountAuthorizationService,
                    new TransactionReadHotPathMetrics(meterRegistry),
                    new TransactionReadSingleFlight(),
                    new TransactionReadAccountFairnessLimiter(2)))
            .addFilters(new BootstrapHeaderAuthenticationFilter("X-Account-Id", "X-Subject"))
            .setCustomArgumentResolvers(new CurrentAuthenticatedPrincipalArgumentResolver())
            .build();
  }

  @Test
  void returnsArchivedCursorPage() throws Exception {
    TransactionCursor nextCursor =
        new TransactionCursor(Instant.parse("2025-01-16T09:00:00Z"), 777L);
    when(transactionArchiveQueryUseCase.getArchivedTransactions(
            argThat(query -> query.accountId() == 101L)))
        .thenReturn(
            new TransactionSlice(
                List.of(
                    new TransactionSummary(
                        777L,
                        101L,
                        "ARCHIVE-TX-777",
                        TransactionDirection.DEBIT,
                        TransactionStatus.BOOKED,
                        1200L,
                        8800L,
                        "KRW",
                        "archived card payment",
                        "STORE",
                        Instant.parse("2025-01-16T09:00:00Z"))),
                nextCursor,
                true,
                20));
    when(requestAccountAuthorizationService.resolveReadableAccountId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(101L)))
        .thenReturn(101L);

    mockMvc
        .perform(
            get("/api/v1/transactions/archive")
                .header("X-Account-Id", "101")
                .param("accountId", "101")
                .param("from", "2025-01-01T00:00:00Z")
                .param("to", "2025-01-31T00:00:00Z")
                .param("limit", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].transactionReference").value("ARCHIVE-TX-777"))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.nextCursor").isString());

    assertTimerCount("archive", "authorization", "success", 1);
    assertTimerCount("archive", "usecase", "success", 1);
    assertTimerCount("archive", "response_mapping", "success", 1);
    assertTimerCount("archive", "total", "success", 1);
  }

  @Test
  void passesArchiveFiltersIntoQuery() throws Exception {
    when(transactionArchiveQueryUseCase.getArchivedTransactions(
            argThat(this::matchesArchiveFilters)))
        .thenReturn(new TransactionSlice(List.of(), null, false, 20));
    when(requestAccountAuthorizationService.resolveReadableAccountId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(101L)))
        .thenReturn(101L);

    mockMvc
        .perform(
            get("/api/v1/transactions/archive")
                .header("X-Account-Id", "101")
                .param("accountId", "101")
                .param("from", "2025-01-01T00:00:00Z")
                .param("to", "2025-01-31T00:00:00Z")
                .param("limit", "20")
                .param("status", "BOOKED")
                .param("direction", "CREDIT")
                .param("minAmountMinor", "1000")
                .param("maxAmountMinor", "2000")
                .param("transactionReference", "ARCHIVE-TX-777"))
        .andExpect(status().isOk());

    verify(transactionArchiveQueryUseCase)
        .getArchivedTransactions(argThat(this::matchesArchiveFilters));
  }

  @Test
  void decodesIncomingArchiveCursor() throws Exception {
    TransactionCursor cursor = new TransactionCursor(Instant.parse("2025-01-16T09:00:00Z"), 777L);
    String encoded = TransactionCursorCodec.encode(cursor);
    when(transactionArchiveQueryUseCase.getArchivedTransactions(
            argThat(query -> cursor.equals(query.cursor()))))
        .thenReturn(new TransactionSlice(List.of(), null, false, 20));
    when(requestAccountAuthorizationService.resolveReadableAccountId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(101L)))
        .thenReturn(101L);

    mockMvc
        .perform(
            get("/api/v1/transactions/archive")
                .header("X-Account-Id", "101")
                .param("accountId", "101")
                .param("from", "2025-01-01T00:00:00Z")
                .param("to", "2025-01-31T00:00:00Z")
                .param("limit", "20")
                .param("cursor", encoded))
        .andExpect(status().isOk());

    verify(transactionArchiveQueryUseCase)
        .getArchivedTransactions(argThat(query -> cursor.equals(query.cursor())));
  }

  @Test
  void rejectsArchiveDateRangeLargerThanThirtyOneDays() throws Exception {
    when(requestAccountAuthorizationService.resolveReadableAccountId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(101L)))
        .thenReturn(101L);

    mockMvc
        .perform(
            get("/api/v1/transactions/archive")
                .header("X-Account-Id", "101")
                .param("accountId", "101")
                .param("from", "2025-01-01T00:00:00Z")
                .param("to", "2025-03-01T00:00:00Z"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void usesLimit50AsDefaultAndHardCapForArchive() throws Exception {
    when(transactionArchiveQueryUseCase.getArchivedTransactions(
            argThat(query -> query.limit() == 50)))
        .thenReturn(new TransactionSlice(List.of(), null, false, 50));
    when(requestAccountAuthorizationService.resolveReadableAccountId(
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(101L)))
        .thenReturn(101L);

    mockMvc
        .perform(
            get("/api/v1/transactions/archive")
                .header("X-Account-Id", "101")
                .param("accountId", "101")
                .param("from", "2025-01-01T00:00:00Z")
                .param("to", "2025-01-31T00:00:00Z"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.limit").value(50));

    mockMvc
        .perform(
            get("/api/v1/transactions/archive")
                .header("X-Account-Id", "101")
                .param("accountId", "101")
                .param("from", "2025-01-01T00:00:00Z")
                .param("to", "2025-01-31T00:00:00Z")
                .param("limit", "51"))
        .andExpect(status().isBadRequest());
  }

  private boolean matchesArchiveFilters(TransactionQuery query) {
    return query.accountId() == 101L
        && query.status() == TransactionStatus.BOOKED
        && query.direction() == TransactionDirection.CREDIT
        && Long.valueOf(1000L).equals(query.minAmountMinor())
        && Long.valueOf(2000L).equals(query.maxAmountMinor())
        && "ARCHIVE-TX-777".equals(query.transactionReference());
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
