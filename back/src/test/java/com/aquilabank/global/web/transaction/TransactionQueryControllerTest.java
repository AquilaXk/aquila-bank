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
import com.aquilabank.domain.transaction.model.TransactionSlice;
import com.aquilabank.domain.transaction.model.TransactionStatus;
import com.aquilabank.domain.transaction.model.TransactionSummary;
import com.aquilabank.domain.transaction.usecase.TransactionQueryUseCase;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TransactionQueryControllerTest {

  private TransactionQueryUseCase transactionQueryUseCase;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    transactionQueryUseCase = mock(TransactionQueryUseCase.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TransactionQueryController(transactionQueryUseCase))
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

    mockMvc
        .perform(
            get("/api/v1/transactions")
                .param("accountId", "101")
                .param("from", "2026-04-01T00:00:00Z")
                .param("to", "2026-04-17T00:00:00Z")
                .param("limit", "20"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].transactionReference").value("TX-777"))
        .andExpect(jsonPath("$.items[0].status").value("BOOKED"))
        .andExpect(jsonPath("$.hasNext").value(true))
        .andExpect(jsonPath("$.nextCursor").isString());
  }

  @Test
  void rejectsTooLargeDateRange() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/transactions")
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

    mockMvc
        .perform(
            get("/api/v1/transactions")
                .param("accountId", "101")
                .param("from", "2026-04-01T00:00:00Z")
                .param("to", "2026-04-17T00:00:00Z")
                .param("limit", "20")
                .param("cursor", encoded))
        .andExpect(status().isOk());

    verify(transactionQueryUseCase)
        .getTransactions(argThat(query -> cursor.equals(query.cursor())));
  }
}
