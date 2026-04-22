package com.aquilabank.global.web.auth;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.auth.exception.DuplicateLoginIdException;
import com.aquilabank.domain.auth.model.MembershipRole;
import com.aquilabank.domain.auth.model.MembershipStatus;
import com.aquilabank.domain.auth.model.UserAccountMembership;
import com.aquilabank.domain.auth.model.UserBootstrapResult;
import com.aquilabank.domain.auth.model.UserStatus;
import com.aquilabank.domain.auth.usecase.UserAccountMembershipUpsertUseCase;
import com.aquilabank.domain.auth.usecase.UserBootstrapUseCase;
import com.aquilabank.global.security.InternalServiceScope;
import com.aquilabank.global.security.InternalServiceTokenTestSupport;
import com.aquilabank.global.web.ApiExceptionHandler;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthBootstrapControllerTest {

  private UserBootstrapUseCase userBootstrapUseCase;
  private UserAccountMembershipUpsertUseCase userAccountMembershipUpsertUseCase;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    userBootstrapUseCase = mock(UserBootstrapUseCase.class);
    userAccountMembershipUpsertUseCase = mock(UserAccountMembershipUpsertUseCase.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new AuthBootstrapController(
                    userBootstrapUseCase,
                    userAccountMembershipUpsertUseCase,
                    InternalServiceTokenTestSupport.authorizer()))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void bootstrapsUserWithInternalServiceToken() throws Exception {
    when(userBootstrapUseCase.bootstrap(argThat(command -> "alice".equals(command.loginId()))))
        .thenReturn(
            new UserBootstrapResult(
                21L, "alice", "Alice", UserStatus.ACTIVE, Instant.parse("2026-04-16T10:30:00Z")));

    mockMvc
        .perform(
            post("/internal/api/v1/auth/users/bootstrap")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        "auth-bootstrap-test", InternalServiceScope.AUTH_BOOTSTRAP))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "loginId": "alice",
                      "password": "password123!",
                      "displayName": "Alice"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(21))
        .andExpect(jsonPath("$.loginId").value("alice"))
        .andExpect(jsonPath("$.userStatus").value("ACTIVE"));

    verify(userBootstrapUseCase)
        .bootstrap(argThat(command -> "password123!".equals(command.password())));
  }

  @Test
  void upsertsMembershipWithInternalServiceToken() throws Exception {
    when(userAccountMembershipUpsertUseCase.upsert(argThat(command -> command.accountId() == 101L)))
        .thenReturn(
            new UserAccountMembership(21L, 101L, MembershipRole.OWNER, MembershipStatus.ACTIVE));

    mockMvc
        .perform(
            put("/internal/api/v1/auth/users/21/memberships/101")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        "auth-bootstrap-test", InternalServiceScope.AUTH_BOOTSTRAP))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "membershipRole": "OWNER",
                      "membershipStatus": "ACTIVE"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(21))
        .andExpect(jsonPath("$.accountId").value(101))
        .andExpect(jsonPath("$.membershipRole").value("OWNER"));

    verify(userAccountMembershipUpsertUseCase)
        .upsert(argThat(command -> command.role() == MembershipRole.OWNER));
  }

  @Test
  void rejectsMissingOrInvalidInternalServiceToken() throws Exception {
    mockMvc
        .perform(
            post("/internal/api/v1/auth/users/bootstrap")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "loginId": "alice",
                      "password": "password123!",
                      "displayName": "Alice"
                    }
                    """))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.message").value("internal service token is invalid"));
  }

  @Test
  void rejectsDuplicateLoginIdAsConflictWithReasonCode() throws Exception {
    when(userBootstrapUseCase.bootstrap(argThat(command -> "alice".equals(command.loginId()))))
        .thenThrow(new DuplicateLoginIdException("loginId is already used"));

    mockMvc
        .perform(
            post("/internal/api/v1/auth/users/bootstrap")
                .header(
                    "Authorization",
                    InternalServiceTokenTestSupport.authorization(
                        "auth-bootstrap-test", InternalServiceScope.AUTH_BOOTSTRAP))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "loginId": "alice",
                      "password": "password123!",
                      "displayName": "Alice"
                    }
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.reasonCode").value("DUPLICATE_LOGIN_ID"))
        .andExpect(jsonPath("$.message").value("loginId is already used"));
  }
}
