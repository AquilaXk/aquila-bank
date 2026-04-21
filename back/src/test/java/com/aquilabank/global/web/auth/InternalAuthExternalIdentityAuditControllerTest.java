package com.aquilabank.global.web.auth;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.auth.exception.ExternalIdentityAuditNotFoundException;
import com.aquilabank.domain.auth.model.AuthStatusChangeOutcome;
import com.aquilabank.domain.auth.model.AuthStatusChangeReason;
import com.aquilabank.domain.auth.model.AuthStatusChangeReasonCode;
import com.aquilabank.domain.auth.model.ExternalIdentityAuditSummary;
import com.aquilabank.domain.auth.model.ExternalIdentityChangeType;
import com.aquilabank.domain.auth.usecase.ExternalIdentityAuditQueryUseCase;
import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenTestSupport;
import com.aquilabank.global.web.ApiExceptionHandler;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InternalAuthExternalIdentityAuditControllerTest {

  private ExternalIdentityAuditQueryUseCase externalIdentityAuditQueryUseCase;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    externalIdentityAuditQueryUseCase = mock(ExternalIdentityAuditQueryUseCase.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new InternalAuthExternalIdentityAuditController(
                    externalIdentityAuditQueryUseCase,
                    InternalServiceTokenTestSupport.authorizer()))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void getsExternalIdentityAuditByRequestId() throws Exception {
    when(externalIdentityAuditQueryUseCase.getByRequestId("external-identity-link-request"))
        .thenReturn(
            new ExternalIdentityAuditSummary(
                "external-identity-link-request",
                "ops-admin",
                ExternalIdentityChangeType.LINK,
                21L,
                "google",
                "0".repeat(64),
                new AuthStatusChangeReason(
                    AuthStatusChangeReasonCode.OPS_MANUAL, "oidc onboarding"),
                AuthStatusChangeOutcome.SUCCESS,
                Instant.parse("2026-04-20T11:00:00Z")));

    mockMvc
        .perform(
            get("/internal/api/v1/auth/external-identity-audits/by-request-id")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        "ops-admin", InternalServiceScope.AUTH_ADMIN))
                .param("requestId", "external-identity-link-request"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestId").value("external-identity-link-request"))
        .andExpect(jsonPath("$.actorSubject").value("ops-admin"))
        .andExpect(jsonPath("$.changeType").value("LINK"))
        .andExpect(jsonPath("$.userId").value(21))
        .andExpect(jsonPath("$.providerId").value("google"))
        .andExpect(jsonPath("$.subjectHash").value("0".repeat(64)))
        .andExpect(jsonPath("$.reasonCode").value("OPS_MANUAL"))
        .andExpect(jsonPath("$.reasonDetail").value("oidc onboarding"))
        .andExpect(jsonPath("$.reason").value("oidc onboarding"))
        .andExpect(jsonPath("$.outcome").value("SUCCESS"))
        .andExpect(jsonPath("$.createdAt").value("2026-04-20T11:00:00Z"));
  }

  @Test
  void returnsNotFoundWhenExternalIdentityAuditRequestIdDoesNotExist() throws Exception {
    when(externalIdentityAuditQueryUseCase.getByRequestId("missing-request"))
        .thenThrow(new ExternalIdentityAuditNotFoundException("audit record is not found"));

    mockMvc
        .perform(
            get("/internal/api/v1/auth/external-identity-audits/by-request-id")
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
            get("/internal/api/v1/auth/external-identity-audits/by-request-id")
                .param("requestId", "external-identity-link-request"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("internal service token is invalid"));
  }
}
