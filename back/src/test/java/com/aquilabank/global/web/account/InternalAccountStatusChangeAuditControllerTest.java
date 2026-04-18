package com.aquilabank.global.web.account;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.account.exception.AccountStatusChangeAuditNotFoundException;
import com.aquilabank.domain.account.model.AccountStatusChangeAuditSummary;
import com.aquilabank.domain.account.usecase.AccountStatusChangeAuditQueryUseCase;
import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenTestSupport;
import com.aquilabank.global.web.ApiExceptionHandler;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InternalAccountStatusChangeAuditControllerTest {

  private AccountStatusChangeAuditQueryUseCase accountStatusChangeAuditQueryUseCase;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    accountStatusChangeAuditQueryUseCase = mock(AccountStatusChangeAuditQueryUseCase.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new InternalAccountStatusChangeAuditController(
                    accountStatusChangeAuditQueryUseCase,
                    InternalServiceTokenTestSupport.authorizer()))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void getsAuditByRequestId() throws Exception {
    when(accountStatusChangeAuditQueryUseCase.getByRequestId("account-lock-request"))
        .thenReturn(
            new AccountStatusChangeAuditSummary(
                "account-lock-request",
                "ops-account-admin",
                101L,
                "ACTIVE",
                "LOCKED",
                Instant.parse("2026-04-18T03:10:00Z")));

    mockMvc
        .perform(
            get("/internal/api/v1/accounts/status-change-audits/by-request-id")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        "ops-account-admin", InternalServiceScope.ACCOUNT_ADMIN))
                .param("requestId", "account-lock-request"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestId").value("account-lock-request"))
        .andExpect(jsonPath("$.actorSubject").value("ops-account-admin"))
        .andExpect(jsonPath("$.targetAccountId").value(101))
        .andExpect(jsonPath("$.beforeStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.afterStatus").value("LOCKED"))
        .andExpect(jsonPath("$.createdAt").value("2026-04-18T03:10:00Z"));
  }

  @Test
  void returnsNotFoundWhenRequestIdDoesNotExist() throws Exception {
    when(accountStatusChangeAuditQueryUseCase.getByRequestId("missing-request"))
        .thenThrow(new AccountStatusChangeAuditNotFoundException("audit record is not found"));

    mockMvc
        .perform(
            get("/internal/api/v1/accounts/status-change-audits/by-request-id")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        "ops-account-admin", InternalServiceScope.ACCOUNT_ADMIN))
                .param("requestId", "missing-request"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("audit record is not found"));
  }

  @Test
  void rejectsMissingOrInvalidInternalServiceToken() throws Exception {
    mockMvc
        .perform(
            get("/internal/api/v1/accounts/status-change-audits/by-request-id")
                .param("requestId", "account-lock-request"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("internal service token is invalid"));
  }
}
