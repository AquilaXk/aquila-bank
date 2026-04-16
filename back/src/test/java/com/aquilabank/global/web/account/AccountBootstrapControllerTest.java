package com.aquilabank.global.web.account;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.account.model.AccountBootstrapResult;
import com.aquilabank.domain.account.usecase.AccountBootstrapUseCase;
import com.aquilabank.global.security.AccountBootstrapApiProperties;
import com.aquilabank.global.web.ApiExceptionHandler;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountBootstrapControllerTest {

  private static final String TOKEN_HEADER = "X-Bootstrap-Token";
  private static final String TOKEN = "test-bootstrap-api-token";

  private AccountBootstrapUseCase accountBootstrapUseCase;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    accountBootstrapUseCase = mock(AccountBootstrapUseCase.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new AccountBootstrapController(
                    accountBootstrapUseCase,
                    new AccountBootstrapApiProperties(true, TOKEN_HEADER, TOKEN)))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void bootstrapsAccountWithSharedToken() throws Exception {
    when(accountBootstrapUseCase.bootstrap(
            argThat(command -> "main account".equals(command.displayName()))))
        .thenReturn(
            new AccountBootstrapResult(
                101L,
                "10000000000001",
                "main account",
                "KRW",
                10_000L,
                "ACTIVE",
                Instant.parse("2026-04-16T10:00:00Z")));

    mockMvc
        .perform(
            post("/internal/api/v1/accounts/bootstrap")
                .header(TOKEN_HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "displayName": "main account",
                      "currencyCode": "KRW",
                      "initialBalanceMinor": 10000
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(101))
        .andExpect(jsonPath("$.accountNumber").value("10000000000001"))
        .andExpect(jsonPath("$.availableBalanceMinor").value(10000));

    verify(accountBootstrapUseCase)
        .bootstrap(argThat(command -> command.initialBalanceMinor() == 10_000L));
  }

  @Test
  void rejectsMissingOrInvalidBootstrapToken() throws Exception {
    mockMvc
        .perform(
            post("/internal/api/v1/accounts/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "displayName": "main account",
                      "currencyCode": "KRW",
                      "initialBalanceMinor": 10000
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("bootstrap token is invalid"));
  }

  @Test
  void rejectsInvalidBody() throws Exception {
    mockMvc
        .perform(
            post("/internal/api/v1/accounts/bootstrap")
                .header(TOKEN_HEADER, TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "displayName": "",
                      "currencyCode": "krw",
                      "initialBalanceMinor": -1
                    }
                    """))
        .andExpect(status().isBadRequest());
  }
}
