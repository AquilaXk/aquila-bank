package com.aquilabank.global.web.account;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.account.model.AccountSummary;
import com.aquilabank.domain.account.usecase.AccountStatusUpdateUseCase;
import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenTestSupport;
import com.aquilabank.global.web.ApiExceptionHandler;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InternalAccountAdminControllerTest {
  private static final String SUBJECT = "account-admin-test";
  private static final String REQUEST_ID_HEADER = "X-Request-Id";

  private AccountStatusUpdateUseCase accountStatusUpdateUseCase;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    accountStatusUpdateUseCase = mock(AccountStatusUpdateUseCase.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new InternalAccountAdminController(
                    accountStatusUpdateUseCase, InternalServiceTokenTestSupport.authorizer()))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void updatesAccountStatusWithInternalAdminScope() throws Exception {
    when(accountStatusUpdateUseCase.update(
            argThat(
                command ->
                    command.accountId() == 101L
                        && command.status().name().equals("LOCKED")
                        && command.actorSubject().equals(SUBJECT)
                        && command.requestId().equals("account-lock-request"))))
        .thenReturn(
            new AccountSummary(
                101L,
                "10000000000001",
                "main account",
                "LOCKED",
                "KRW",
                12_000L,
                0L,
                Instant.parse("2026-04-18T03:00:00Z"),
                Instant.parse("2026-04-18T03:10:00Z")));

    mockMvc
        .perform(
            put("/internal/api/v1/accounts/101/status")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.ACCOUNT_ADMIN))
                .header(REQUEST_ID_HEADER, "account-lock-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accountStatus": "LOCKED"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accountId").value(101))
        .andExpect(jsonPath("$.accountStatus").value("LOCKED"))
        .andExpect(jsonPath("$.accountNumber").value("10000000000001"));

    verify(accountStatusUpdateUseCase)
        .update(
            argThat(
                command ->
                    command.accountId() == 101L
                        && command.status().name().equals("LOCKED")
                        && command.actorSubject().equals(SUBJECT)
                        && command.requestId().equals("account-lock-request")));
  }

  @Test
  void rejectsMissingOrWrongInternalServiceToken() throws Exception {
    mockMvc
        .perform(
            put("/internal/api/v1/accounts/101/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accountStatus": "LOCKED"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("internal service token is invalid"));

    mockMvc
        .perform(
            put("/internal/api/v1/accounts/101/status")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.ACCOUNT_BOOTSTRAP))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "accountStatus": "LOCKED"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("internal service token is invalid"));
  }

  @Test
  void rejectsInvalidBody() throws Exception {
    mockMvc
        .perform(
            put("/internal/api/v1/accounts/101/status")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.ACCOUNT_ADMIN))
                .header(REQUEST_ID_HEADER, "account-invalid-body-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("accountStatus is required"));
  }
}
