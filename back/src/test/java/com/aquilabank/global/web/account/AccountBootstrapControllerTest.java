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
import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenTestSupport;
import com.aquilabank.global.web.ApiExceptionHandler;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AccountBootstrapControllerTest {

  private AccountBootstrapUseCase accountBootstrapUseCase;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    accountBootstrapUseCase = mock(AccountBootstrapUseCase.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new AccountBootstrapController(
                    accountBootstrapUseCase, InternalServiceTokenTestSupport.authorizer()))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void bootstrapsAccountWithInternalServiceToken() throws Exception {
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
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        "account-bootstrap-test", InternalServiceScope.ACCOUNT_BOOTSTRAP))
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
  void rejectsMissingOrInvalidInternalServiceToken() throws Exception {
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
        .andExpect(jsonPath("$.message").value("internal service token is invalid"));
  }

  @Test
  void rejectsInvalidBody() throws Exception {
    mockMvc
        .perform(
            post("/internal/api/v1/accounts/bootstrap")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        "account-bootstrap-test", InternalServiceScope.ACCOUNT_BOOTSTRAP))
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
