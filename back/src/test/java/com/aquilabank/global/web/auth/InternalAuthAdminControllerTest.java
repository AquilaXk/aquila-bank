package com.aquilabank.global.web.auth;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.auth.exception.DuplicateExternalIdentityMappingException;
import com.aquilabank.domain.auth.exception.PasswordRecoveryTokenNotFoundException;
import com.aquilabank.domain.auth.model.AuthUserSummary;
import com.aquilabank.domain.auth.model.ExternalIdentityMapping;
import com.aquilabank.domain.auth.model.MembershipRole;
import com.aquilabank.domain.auth.model.MembershipStatus;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenLookupView;
import com.aquilabank.domain.auth.model.PasswordRecoveryTokenStatus;
import com.aquilabank.domain.auth.model.UserAccountMembershipSummary;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.usecase.AuthUserQueryUseCase;
import com.aquilabank.domain.auth.usecase.ExternalIdentityMappingLinkUseCase;
import com.aquilabank.domain.auth.usecase.ExternalIdentityMappingUnlinkUseCase;
import com.aquilabank.domain.auth.usecase.PasswordRecoveryTokenQueryUseCase;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipQueryUseCase;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipStatusUpdateUseCase;
import com.aquilabank.domain.auth.usecase.UserStatusUpdateUseCase;
import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenTestSupport;
import com.aquilabank.global.web.ApiExceptionHandler;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class InternalAuthAdminControllerTest {
  private static final String SUBJECT = "ops-admin";
  private static final String REQUEST_ID_HEADER = "X-Request-Id";

  private AuthUserQueryUseCase authUserQueryUseCase;
  private UserStatusUpdateUseCase userStatusUpdateUseCase;
  private UserAccountMembershipQueryUseCase userAccountMembershipQueryUseCase;
  private UserAccountMembershipStatusUpdateUseCase userAccountMembershipStatusUpdateUseCase;
  private PasswordRecoveryTokenQueryUseCase passwordRecoveryTokenQueryUseCase;
  private ExternalIdentityMappingLinkUseCase externalIdentityMappingLinkUseCase;
  private ExternalIdentityMappingUnlinkUseCase externalIdentityMappingUnlinkUseCase;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    authUserQueryUseCase = mock(AuthUserQueryUseCase.class);
    userStatusUpdateUseCase = mock(UserStatusUpdateUseCase.class);
    userAccountMembershipQueryUseCase = mock(UserAccountMembershipQueryUseCase.class);
    userAccountMembershipStatusUpdateUseCase = mock(UserAccountMembershipStatusUpdateUseCase.class);
    passwordRecoveryTokenQueryUseCase = mock(PasswordRecoveryTokenQueryUseCase.class);
    externalIdentityMappingLinkUseCase = mock(ExternalIdentityMappingLinkUseCase.class);
    externalIdentityMappingUnlinkUseCase = mock(ExternalIdentityMappingUnlinkUseCase.class);

    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new InternalAuthAdminController(
                    authUserQueryUseCase,
                    userStatusUpdateUseCase,
                    userAccountMembershipQueryUseCase,
                    userAccountMembershipStatusUpdateUseCase,
                    passwordRecoveryTokenQueryUseCase,
                    externalIdentityMappingLinkUseCase,
                    externalIdentityMappingUnlinkUseCase,
                    InternalServiceTokenTestSupport.authorizer()))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void getsUserByUserIdAndLoginId() throws Exception {
    AuthUserSummary summary =
        new AuthUserSummary(
            21L,
            "alice",
            "Alice",
            UserStatus.ACTIVE,
            Instant.parse("2026-04-16T11:00:00Z"),
            Instant.parse("2026-04-16T11:05:00Z"));

    when(authUserQueryUseCase.getByUserId(21L)).thenReturn(summary);
    when(authUserQueryUseCase.getByLoginId("alice")).thenReturn(summary);

    mockMvc
        .perform(
            get("/internal/api/v1/auth/users/21")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.AUTH_ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(21))
        .andExpect(jsonPath("$.loginId").value("alice"))
        .andExpect(jsonPath("$.userStatus").value("ACTIVE"));

    mockMvc
        .perform(
            get("/internal/api/v1/auth/users/by-login-id")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .param("loginId", "alice"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Alice"));
  }

  @Test
  void getsPasswordRecoveryTokenByRequestId() throws Exception {
    when(passwordRecoveryTokenQueryUseCase.getByHandoffRequestId("handoff-request-001"))
        .thenReturn(
            new PasswordRecoveryTokenLookupView(
                "handoff-request-001",
                21L,
                "alice",
                "recovery-token",
                PasswordRecoveryTokenStatus.USED,
                Instant.parse("2026-04-16T12:00:00Z"),
                Instant.parse("2026-04-16T12:05:00Z"),
                Instant.parse("2026-04-16T11:00:00Z")));

    mockMvc
        .perform(
            get("/internal/api/v1/auth/password-recovery-tokens/by-request-id")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .param("requestId", "handoff-request-001"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.requestId").value("handoff-request-001"))
        .andExpect(jsonPath("$.userId").value(21))
        .andExpect(jsonPath("$.loginId").value("alice"))
        .andExpect(jsonPath("$.recoveryToken").value("recovery-token"))
        .andExpect(jsonPath("$.tokenStatus").value("USED"))
        .andExpect(jsonPath("$.expiresAt").value("2026-04-16T12:00:00Z"))
        .andExpect(jsonPath("$.usedAt").value("2026-04-16T12:05:00Z"))
        .andExpect(jsonPath("$.createdAt").value("2026-04-16T11:00:00Z"));
  }

  @Test
  void rejectsMissingOrBlankRequestIdForPasswordRecoveryLookup() throws Exception {
    when(passwordRecoveryTokenQueryUseCase.getByHandoffRequestId(" "))
        .thenThrow(new IllegalArgumentException("handoffRequestId is required"));

    mockMvc
        .perform(
            get("/internal/api/v1/auth/password-recovery-tokens/by-request-id")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.AUTH_ADMIN)))
        .andExpect(status().isBadRequest());

    mockMvc
        .perform(
            get("/internal/api/v1/auth/password-recovery-tokens/by-request-id")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .param("requestId", " "))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsMissingOrInvalidInternalServiceTokenForPasswordRecoveryLookup() throws Exception {
    mockMvc
        .perform(
            get("/internal/api/v1/auth/password-recovery-tokens/by-request-id")
                .param("requestId", "handoff-request-001"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("internal service token is invalid"));

    mockMvc
        .perform(
            get("/internal/api/v1/auth/password-recovery-tokens/by-request-id")
                .header("Authorization", "Bearer invalid-token")
                .param("requestId", "handoff-request-001"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("internal service token is invalid"));
  }

  @Test
  void linksAndUnlinksExternalIdentityMapping() throws Exception {
    ExternalIdentityMapping mapping =
        new ExternalIdentityMapping(
            21L,
            "google",
            "google-subject-001",
            Instant.parse("2026-04-20T11:00:00Z"),
            Instant.parse("2026-04-20T11:00:00Z"));

    when(externalIdentityMappingLinkUseCase.link(argThat(command -> command.userId() == 21L)))
        .thenReturn(mapping);
    when(externalIdentityMappingUnlinkUseCase.unlink(argThat(command -> command.userId() == 21L)))
        .thenReturn(mapping);

    mockMvc
        .perform(
            post("/internal/api/v1/auth/users/21/external-identities")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .header(REQUEST_ID_HEADER, "external-identity-link-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "providerId": "google",
                      "subject": "google-subject-001",
                      "reasonCode": "OPS_MANUAL",
                      "reasonDetail": "oidc onboarding"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(21))
        .andExpect(jsonPath("$.providerId").value("google"))
        .andExpect(jsonPath("$.subject").value("google-subject-001"))
        .andExpect(jsonPath("$.createdAt").value("2026-04-20T11:00:00Z"))
        .andExpect(jsonPath("$.updatedAt").value("2026-04-20T11:00:00Z"));

    mockMvc
        .perform(
            delete("/internal/api/v1/auth/users/21/external-identities")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .header(REQUEST_ID_HEADER, "external-identity-unlink-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "providerId": "google",
                      "subject": "google-subject-001",
                      "reasonCode": "OPS_MANUAL",
                      "reasonDetail": "oidc unlink"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(21))
        .andExpect(jsonPath("$.providerId").value("google"))
        .andExpect(jsonPath("$.subject").value("google-subject-001"));

    verify(externalIdentityMappingLinkUseCase)
        .link(
            argThat(
                command ->
                    command.userId() == 21L
                        && command.providerId().equals("google")
                        && command.subject().equals("google-subject-001")
                        && command.normalizedReason().reasonCode().name().equals("OPS_MANUAL")
                        && command.normalizedReason().reasonDetail().equals("oidc onboarding")
                        && command.actorSubject().equals(SUBJECT)
                        && command.requestId().equals("external-identity-link-request")));
    verify(externalIdentityMappingUnlinkUseCase)
        .unlink(
            argThat(
                command ->
                    command.userId() == 21L
                        && command.providerId().equals("google")
                        && command.subject().equals("google-subject-001")
                        && command.normalizedReason().reasonCode().name().equals("OPS_MANUAL")
                        && command.normalizedReason().reasonDetail().equals("oidc unlink")
                        && command.actorSubject().equals(SUBJECT)
                        && command.requestId().equals("external-identity-unlink-request")));
  }

  @Test
  void rejectsMissingStructuredReasonForExternalIdentityMapping() throws Exception {
    mockMvc
        .perform(
            post("/internal/api/v1/auth/users/21/external-identities")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .header(REQUEST_ID_HEADER, "external-identity-missing-reason-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "providerId": "google",
                      "subject": "google-subject-001"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("reasonCode is required"));

    verifyNoInteractions(externalIdentityMappingLinkUseCase);
  }

  @Test
  void rejectsDuplicateExternalIdentityMappingAsConflict() throws Exception {
    when(externalIdentityMappingLinkUseCase.link(argThat(command -> command.userId() == 21L)))
        .thenThrow(
            new DuplicateExternalIdentityMappingException(
                "external identity mapping already exists"));

    mockMvc
        .perform(
            post("/internal/api/v1/auth/users/21/external-identities")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .header(REQUEST_ID_HEADER, "external-identity-duplicate-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "providerId": "google",
                      "subject": "google-subject-001",
                      "reasonCode": "OPS_MANUAL",
                      "reasonDetail": "duplicate check"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("external identity mapping already exists"));
  }

  @Test
  void returnsNotFoundWhenPasswordRecoveryTokenRequestIdIsUnknown() throws Exception {
    when(passwordRecoveryTokenQueryUseCase.getByHandoffRequestId("missing-handoff-request"))
        .thenThrow(
            new PasswordRecoveryTokenNotFoundException("password recovery token is not found"));

    mockMvc
        .perform(
            get("/internal/api/v1/auth/password-recovery-tokens/by-request-id")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .param("requestId", "missing-handoff-request"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("password recovery token is not found"));
  }

  @Test
  void getsMembershipAndUpdatesStatuses() throws Exception {
    UserAccountMembershipSummary membershipSummary =
        new UserAccountMembershipSummary(
            21L,
            101L,
            MembershipRole.OWNER,
            MembershipStatus.ACTIVE,
            Instant.parse("2026-04-16T11:00:00Z"),
            Instant.parse("2026-04-16T11:05:00Z"));
    AuthUserSummary disabledUser =
        new AuthUserSummary(
            21L,
            "alice",
            "Alice",
            UserStatus.DISABLED,
            Instant.parse("2026-04-16T11:00:00Z"),
            Instant.parse("2026-04-16T11:06:00Z"));
    UserAccountMembershipSummary revokedMembership =
        new UserAccountMembershipSummary(
            21L,
            101L,
            MembershipRole.OWNER,
            MembershipStatus.REVOKED,
            Instant.parse("2026-04-16T11:00:00Z"),
            Instant.parse("2026-04-16T11:06:00Z"));

    when(userAccountMembershipQueryUseCase.getByUserIdAndAccountId(21L, 101L))
        .thenReturn(membershipSummary);
    when(userStatusUpdateUseCase.update(argThat(command -> command.userId() == 21L)))
        .thenReturn(disabledUser);
    when(userAccountMembershipStatusUpdateUseCase.update(
            argThat(command -> command.accountId() == 101L)))
        .thenReturn(revokedMembership);

    mockMvc
        .perform(
            get("/internal/api/v1/auth/users/21/memberships/101")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.AUTH_ADMIN)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.membershipRole").value("OWNER"))
        .andExpect(jsonPath("$.membershipStatus").value("ACTIVE"));

    mockMvc
        .perform(
            put("/internal/api/v1/auth/users/21/status")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .header(REQUEST_ID_HEADER, "user-status-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "userStatus": "DISABLED",
                      "reasonCode": "FRAUD_REVIEW",
                      "reasonDetail": "fraud-review"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userStatus").value("DISABLED"));

    mockMvc
        .perform(
            put("/internal/api/v1/auth/users/21/memberships/101/status")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .header(REQUEST_ID_HEADER, "membership-status-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "membershipStatus": "REVOKED",
                      "reason": "manual-revoke"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.membershipStatus").value("REVOKED"));

    verify(userStatusUpdateUseCase)
        .update(
            argThat(
                command ->
                    command.status() == UserStatus.DISABLED
                        && command.reasonCode().name().equals("FRAUD_REVIEW")
                        && command.reasonDetail().equals("fraud-review")
                        && command.reason().equals("fraud-review")
                        && command.actorSubject().equals(SUBJECT)
                        && command.requestId().equals("user-status-request")));
    verify(userAccountMembershipStatusUpdateUseCase)
        .update(
            argThat(
                command ->
                    command.status() == MembershipStatus.REVOKED
                        && command.reasonCode().name().equals("LEGACY_FREE_TEXT")
                        && command.reasonDetail().equals("manual-revoke")
                        && command.reason().equals("manual-revoke")
                        && command.actorSubject().equals(SUBJECT)
                        && command.requestId().equals("membership-status-request")));
  }

  @Test
  void rejectsMissingReasonOrActorSubject() throws Exception {
    mockMvc
        .perform(
            put("/internal/api/v1/auth/users/21/status")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .header(REQUEST_ID_HEADER, "missing-reason-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "userStatus": "DISABLED"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("reasonCode is required"));

    mockMvc
        .perform(
            put("/internal/api/v1/auth/users/21/memberships/101/status")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .header(REQUEST_ID_HEADER, "missing-subject-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "membershipStatus": "REVOKED",
                      "reasonCode": "OPS_MANUAL"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("reasonDetail is required"));
  }

  @Test
  void acceptsLegacyReasonAsFallbackCode() throws Exception {
    AuthUserSummary disabledUser =
        new AuthUserSummary(
            21L,
            "alice",
            "Alice",
            UserStatus.DISABLED,
            Instant.parse("2026-04-16T11:00:00Z"),
            Instant.parse("2026-04-16T11:06:00Z"));

    when(userStatusUpdateUseCase.update(argThat(command -> command.userId() == 21L)))
        .thenReturn(disabledUser);

    mockMvc
        .perform(
            put("/internal/api/v1/auth/users/21/status")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .header(REQUEST_ID_HEADER, "legacy-reason-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "userStatus": "DISABLED",
                      "reason": "legacy-free-text"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userStatus").value("DISABLED"));

    verify(userStatusUpdateUseCase)
        .update(
            argThat(
                command ->
                    command.status() == UserStatus.DISABLED
                        && command.reasonCode().name().equals("LEGACY_FREE_TEXT")
                        && command.reasonDetail().equals("legacy-free-text")
                        && command.requestId().equals("legacy-reason-request")));
  }

  @Test
  void acceptsLegacyReasonForMembershipStatusAsFallbackCode() throws Exception {
    UserAccountMembershipSummary revokedMembership =
        new UserAccountMembershipSummary(
            21L,
            101L,
            MembershipRole.OWNER,
            MembershipStatus.REVOKED,
            Instant.parse("2026-04-16T11:00:00Z"),
            Instant.parse("2026-04-16T11:06:00Z"));

    when(userAccountMembershipStatusUpdateUseCase.update(
            argThat(command -> command.accountId() == 101L)))
        .thenReturn(revokedMembership);

    mockMvc
        .perform(
            put("/internal/api/v1/auth/users/21/memberships/101/status")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .header(REQUEST_ID_HEADER, "membership-legacy-reason-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "membershipStatus": "REVOKED",
                      "reason": "manual-revoke"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.membershipStatus").value("REVOKED"));

    verify(userAccountMembershipStatusUpdateUseCase)
        .update(
            argThat(
                command ->
                    command.status() == MembershipStatus.REVOKED
                        && command.reasonCode().name().equals("LEGACY_FREE_TEXT")
                        && command.reasonDetail().equals("manual-revoke")
                        && command.reason().equals("manual-revoke")
                        && command.actorSubject().equals(SUBJECT)
                        && command.requestId().equals("membership-legacy-reason-request")));
  }

  @Test
  void rejectsMixedReasonAndReasonCodeForUserStatusUpdate() throws Exception {
    mockMvc
        .perform(
            put("/internal/api/v1/auth/users/21/status")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .header(REQUEST_ID_HEADER, "user-mixed-reason-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "userStatus": "DISABLED",
                      "reasonCode": "FRAUD_REVIEW",
                      "reason": "legacy-free-text"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message")
                .value("reason and reasonCode/reasonDetail cannot be used together"));

    verifyNoInteractions(userStatusUpdateUseCase);
  }

  @Test
  void rejectsMixedReasonAndReasonDetailForMembershipStatusUpdate() throws Exception {
    mockMvc
        .perform(
            put("/internal/api/v1/auth/users/21/memberships/101/status")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        SUBJECT, InternalServiceScope.AUTH_ADMIN))
                .header(REQUEST_ID_HEADER, "membership-mixed-reason-request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "membershipStatus": "REVOKED",
                      "reasonDetail": "manual-revoke",
                      "reason": "legacy-free-text"
                    }
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(
            jsonPath("$.message")
                .value("reason and reasonCode/reasonDetail cannot be used together"));

    verifyNoInteractions(userAccountMembershipStatusUpdateUseCase);
  }

  @Test
  void rejectsMissingOrInvalidInternalServiceToken() throws Exception {
    mockMvc
        .perform(get("/internal/api/v1/auth/users/21"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("internal service token is invalid"));
  }
}
