package com.aquilabank.global.web.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.auth.exception.AuthStatusChangeAuditNotFoundException;
import com.aquilabank.domain.auth.model.AuthStatusChangeAuditSummary;
import com.aquilabank.domain.auth.model.AuthStatusChangeOutcome;
import com.aquilabank.domain.auth.model.AuthStatusChangeReason;
import com.aquilabank.domain.auth.model.AuthStatusChangeReasonCode;
import com.aquilabank.domain.auth.model.AuthStatusChangeType;
import com.aquilabank.domain.auth.usecase.AuthStatusChangeAuditQueryUseCase;
import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenTestSupport;
import com.aquilabank.global.web.ApiExceptionHandler;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InternalAuthStatusChangeAuditControllerTest {

  private AuthStatusChangeAuditQueryUseCase authStatusChangeAuditQueryUseCase;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    authStatusChangeAuditQueryUseCase = mock(AuthStatusChangeAuditQueryUseCase.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new InternalAuthStatusChangeAuditController(
                    authStatusChangeAuditQueryUseCase,
                    InternalServiceTokenTestSupport.authorizer()))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void getsAuditByRequestId() throws Exception {
    when(authStatusChangeAuditQueryUseCase.getByRequestId("membership-revoked-request"))
        .thenReturn(
            new AuthStatusChangeAuditSummary(
                "membership-revoked-request",
                "ops-admin",
                AuthStatusChangeType.MEMBERSHIP_STATUS,
                21L,
                101L,
                "ACTIVE",
                "REVOKED",
                new AuthStatusChangeReason(AuthStatusChangeReasonCode.OPS_MANUAL, "manual-revoke"),
                AuthStatusChangeOutcome.SUCCESS,
                Instant.parse("2026-04-16T11:06:00Z")));

    mockMvc
        .perform(
            get("/internal/api/v1/auth/status-change-audits/by-request-id")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        "ops-admin", InternalServiceScope.AUTH_ADMIN))
                .param("requestId", "membership-revoked-request"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestId").value("membership-revoked-request"))
        .andExpect(jsonPath("$.actorSubject").value("ops-admin"))
        .andExpect(jsonPath("$.targetUserId").value(21))
        .andExpect(jsonPath("$.targetAccountId").value(101))
        .andExpect(jsonPath("$.changeType").value("MEMBERSHIP_STATUS"))
        .andExpect(jsonPath("$.beforeStatus").value("ACTIVE"))
        .andExpect(jsonPath("$.afterStatus").value("REVOKED"))
        .andExpect(jsonPath("$.reasonCode").value("OPS_MANUAL"))
        .andExpect(jsonPath("$.reasonDetail").value("manual-revoke"))
        .andExpect(jsonPath("$.reason").value("manual-revoke"))
        .andExpect(jsonPath("$.outcome").value("SUCCESS"))
        .andExpect(jsonPath("$.createdAt").value("2026-04-16T11:06:00Z"));
  }

  @Test
  void returnsNotFoundWhenRequestIdDoesNotExist() throws Exception {
    when(authStatusChangeAuditQueryUseCase.getByRequestId("missing-request"))
        .thenThrow(new AuthStatusChangeAuditNotFoundException("audit record is not found"));

    mockMvc
        .perform(
            get("/internal/api/v1/auth/status-change-audits/by-request-id")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        "ops-admin", InternalServiceScope.AUTH_ADMIN))
                .param("requestId", "missing-request"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("audit record is not found"));
  }

  @Test
  void rejectsMissingOrInvalidInternalServiceToken() throws Exception {
    mockMvc
        .perform(
            get("/internal/api/v1/auth/status-change-audits/by-request-id")
                .param("requestId", "membership-revoked-request"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("internal service token is invalid"));
  }
}
