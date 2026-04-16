package com.aquilabank.global.web.ledger;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.ledger.model.TransferResult;
import com.aquilabank.domain.ledger.usecase.TransferCommandUseCase;
import com.aquilabank.global.security.BootstrapHeaderAuthenticationFilter;
import com.aquilabank.global.web.ApiExceptionHandler;
import com.aquilabank.global.web.security.CurrentAccountIdArgumentResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TransferCommandControllerTest {

  private TransferCommandUseCase transferCommandUseCase;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    transferCommandUseCase = mock(TransferCommandUseCase.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TransferCommandController(transferCommandUseCase))
            .addFilters(new BootstrapHeaderAuthenticationFilter("X-Account-Id", "X-Subject"))
            .setCustomArgumentResolvers(new CurrentAccountIdArgumentResolver())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
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

    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("X-Account-Id", "101")
                .header("Idempotency-Key", "transfer-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
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
  void rejectsMissingAuthenticationHeader() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/transfers")
                .header("Idempotency-Key", "transfer-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
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
                      "targetAccountId": 101,
                      "amountMinor": 0,
                      "currencyCode": "krw",
                      "summary": ""
                    }
                    """))
        .andExpect(status().isBadRequest());
  }
}
