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
import com.aquilabank.domain.auth.model.BackupCodeChallengeVerifyCommand;
import com.aquilabank.domain.auth.model.LoginChallengeType;
import com.aquilabank.domain.auth.model.LoginCommand;
import com.aquilabank.domain.auth.model.LoginResult;
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
import com.aquilabank.global.security.SecurityRememberDeviceProperties;
import com.aquilabank.global.web.ApiExceptionHandler;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LoginControllerRememberDeviceTest {

  private LoginUseCase loginUseCase;
  private BackupCodeChallengeVerifyUseCase backupCodeChallengeVerifyUseCase;
  private AuthSessionMetadataResolver authSessionMetadataResolver;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    loginUseCase = mock(LoginUseCase.class);
    backupCodeChallengeVerifyUseCase = mock(BackupCodeChallengeVerifyUseCase.class);
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
                    mock(TotpChallengeVerifyUseCase.class),
                    mock(TotpDisableUseCase.class),
                    mock(BackupCodeGenerateUseCase.class),
                    backupCodeChallengeVerifyUseCase,
                    mock(LogoutUseCase.class),
                    mock(PasswordResetUseCase.class),
                    mock(PasswordRecoveryRequestUseCase.class),
                    mock(PasswordRecoveryConfirmUseCase.class),
                    authSessionMetadataResolver,
                    mock(LoginThrottleGuard.class),
                    new RememberDeviceCookieManager(
                        new SecurityRememberDeviceProperties(
                            "ab_mfa_remember_device", 2_592_000L))))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void loginUsesRememberDeviceCookieAndSetsRotatedCookieOnSuccess() throws Exception {
    when(loginUseCase.login(
            argThat(
                (LoginCommand command) ->
                    "alice".equals(command.loginId())
                        && "password123!".equals(command.password())
                        && "remember-device-token".equals(command.rememberDeviceToken()))))
        .thenReturn(
            LoginResult.success(
                "access-token",
                "refresh-token",
                "Bearer",
                Instant.parse("2026-04-20T12:00:00Z"),
                Instant.parse("2026-05-04T11:45:00Z"),
                7L,
                "next-remember-device-token"));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .cookie(new Cookie("ab_mfa_remember_device", "remember-device-token"))
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
                    org.hamcrest.Matchers.containsString(
                        "ab_mfa_remember_device=next-remember-device-token")));
  }

  @Test
  void loginClearsStaleRememberDeviceCookieWhenChallengeIsReturned() throws Exception {
    when(loginUseCase.login(any()))
        .thenReturn(
            LoginResult.mfaRequired(
                "challenge-1", LoginChallengeType.TOTP, Instant.parse("2026-04-20T11:35:00Z")));

    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .cookie(new Cookie("ab_mfa_remember_device", "stale-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "loginId": "alice",
                      "password": "password123!"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("MFA_REQUIRED"))
        .andExpect(
            header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
  }

  @Test
  void verifyBackupCodeChallengeSetsRememberDeviceCookieWhenRequested() throws Exception {
    when(backupCodeChallengeVerifyUseCase.verify(
            argThat(
                (BackupCodeChallengeVerifyCommand command) ->
                    "challenge-1".equals(command.challengeId())
                        && "ABCD-EFGH".equals(command.backupCode())
                        && command.rememberDevice())))
        .thenReturn(
            LoginResult.success(
                "access-token",
                "refresh-token",
                "Bearer",
                Instant.parse("2026-04-20T12:00:00Z"),
                Instant.parse("2026-05-04T11:45:00Z"),
                7L,
                "remember-device-token"));

    mockMvc
        .perform(
            post("/api/v1/auth/mfa/backup-codes/challenge/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "challengeId": "challenge-1",
                      "backupCode": "ABCD-EFGH",
                      "rememberDevice": true
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(
            header()
                .string(
                    "Set-Cookie",
                    org.hamcrest.Matchers.containsString(
                        "ab_mfa_remember_device=remember-device-token")));
  }
}
