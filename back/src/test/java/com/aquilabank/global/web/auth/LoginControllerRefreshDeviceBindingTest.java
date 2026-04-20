package com.aquilabank.global.web.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.auth.model.AuthSessionClientMetadata;
import com.aquilabank.domain.auth.model.LoginCommand;
import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.model.TotpChallengeVerifyCommand;
import com.aquilabank.domain.auth.usecase.AuthSessionListUseCase;
import com.aquilabank.domain.auth.usecase.AuthSessionRevokeAllUseCase;
import com.aquilabank.domain.auth.usecase.AuthSessionRevokeUseCase;
import com.aquilabank.domain.auth.usecase.BackupCodeChallengeVerifyUseCase;
import com.aquilabank.domain.auth.usecase.BackupCodeGenerateUseCase;
import com.aquilabank.domain.auth.usecase.LoginUseCase;
import com.aquilabank.domain.auth.usecase.LogoutUseCase;
import com.aquilabank.domain.auth.usecase.PasswordRecoveryConfirmUseCase;
import com.aquilabank.domain.auth.usecase.PasswordRecoveryRequestUseCase;
import com.aquilabank.domain.auth.usecase.PasswordResetUseCase;
import com.aquilabank.domain.auth.usecase.RefreshTokenUseCase;
import com.aquilabank.domain.auth.usecase.TotpChallengeVerifyUseCase;
import com.aquilabank.domain.auth.usecase.TotpDisableUseCase;
import com.aquilabank.domain.auth.usecase.TotpEnrollmentUseCase;
import com.aquilabank.global.security.LoginThrottleGuard;
import com.aquilabank.global.security.SecurityJwtProperties;
import com.aquilabank.global.web.ApiExceptionHandler;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LoginControllerRefreshDeviceBindingTest {

  private LoginUseCase loginUseCase;
  private TotpChallengeVerifyUseCase totpChallengeVerifyUseCase;
  private AuthSessionMetadataResolver authSessionMetadataResolver;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    loginUseCase = mock(LoginUseCase.class);
    totpChallengeVerifyUseCase = mock(TotpChallengeVerifyUseCase.class);
    authSessionMetadataResolver = mock(AuthSessionMetadataResolver.class);
    when(authSessionMetadataResolver.resolve(any()))
        .thenReturn(new AuthSessionClientMetadata("Windows / Chrome", "203.0.113.10"));

    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new LoginController(
                    loginUseCase,
                    mock(AuthSessionListUseCase.class),
                    mock(AuthSessionRevokeUseCase.class),
                    mock(AuthSessionRevokeAllUseCase.class),
                    mock(RefreshTokenUseCase.class),
                    mock(TotpEnrollmentUseCase.class),
                    totpChallengeVerifyUseCase,
                    mock(TotpDisableUseCase.class),
                    mock(BackupCodeGenerateUseCase.class),
                    mock(BackupCodeChallengeVerifyUseCase.class),
                    mock(LogoutUseCase.class),
                    mock(PasswordResetUseCase.class),
                    mock(PasswordRecoveryRequestUseCase.class),
                    mock(PasswordRecoveryConfirmUseCase.class),
                    authSessionMetadataResolver,
                    mock(LoginThrottleGuard.class),
                    new RefreshDeviceBindingCookieManager(
                        new SecurityJwtProperties(
                            "secret", "issuer", 900L, 1_209_600L, "ab_refresh_device"))))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void loginSetsRefreshDeviceBindingCookieOnSuccess() throws Exception {
    when(loginUseCase.login(
            argThat(
                (LoginCommand command) ->
                    "alice".equals(command.loginId())
                        && "password123!".equals(command.password()))))
        .thenReturn(
            LoginResult.success(
                "access-token",
                "refresh-token",
                "Bearer",
                Instant.parse("2026-04-20T12:00:00Z"),
                Instant.parse("2026-05-04T11:45:00Z"),
                7L,
                "binding-token"));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "loginId": "alice",
                      "password": "password123!"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(
            header()
                .string(
                    "Set-Cookie",
                    org.hamcrest.Matchers.containsString("ab_refresh_device=binding-token")));
  }

  @Test
  void verifyTotpChallengeSetsRefreshDeviceBindingCookieOnSuccess() throws Exception {
    when(totpChallengeVerifyUseCase.verify(
            argThat(
                (TotpChallengeVerifyCommand command) ->
                    "challenge-1".equals(command.challengeId())
                        && "123456".equals(command.totpCode()))))
        .thenReturn(
            LoginResult.success(
                "access-token",
                "refresh-token",
                "Bearer",
                Instant.parse("2026-04-20T12:00:00Z"),
                Instant.parse("2026-05-04T11:45:00Z"),
                7L,
                "binding-token"));

    mockMvc
        .perform(
            post("/api/v1/auth/mfa/totp/challenge/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "challengeId": "challenge-1",
                      "totpCode": "123456"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(
            header()
                .string(
                    "Set-Cookie",
                    org.hamcrest.Matchers.containsString("ab_refresh_device=binding-token")));
  }
}
