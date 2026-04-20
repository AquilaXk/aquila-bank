package com.aquilabank.global.web.auth;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.auth.model.AuthUserSummary;
import com.aquilabank.domain.auth.model.MembershipRole;
import com.aquilabank.domain.auth.model.MembershipStatus;
import com.aquilabank.domain.auth.model.UserAccountMembershipSummary;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.usecase.AuthUserQueryUseCase;
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
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    authUserQueryUseCase = mock(AuthUserQueryUseCase.class);
    userStatusUpdateUseCase = mock(UserStatusUpdateUseCase.class);
    userAccountMembershipQueryUseCase = mock(UserAccountMembershipQueryUseCase.class);
    userAccountMembershipStatusUpdateUseCase = mock(UserAccountMembershipStatusUpdateUseCase.class);
    passwordRecoveryTokenQueryUseCase = mock(PasswordRecoveryTokenQueryUseCase.class);

    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new InternalAuthAdminController(
                    authUserQueryUseCase,
                    userStatusUpdateUseCase,
                    userAccountMembershipQueryUseCase,
                    userAccountMembershipStatusUpdateUseCase,
                    passwordRecoveryTokenQueryUseCase,
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
