package com.aquilabank.global.web.auth;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.auth.model.BackupCodeChallengeVerifyCommand;
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
import com.aquilabank.global.web.ApiExceptionHandler;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LoginControllerBackupCodeChallengeTest {

  private BackupCodeChallengeVerifyUseCase backupCodeChallengeVerifyUseCase;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    backupCodeChallengeVerifyUseCase = mock(BackupCodeChallengeVerifyUseCase.class);
    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new LoginController(
                    mock(LoginUseCase.class),
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
                    mock(AuthSessionMetadataResolver.class),
                    mock(LoginThrottleGuard.class)))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void verifiesBackupCodeChallengeAndReturnsTokenPair() throws Exception {
    when(backupCodeChallengeVerifyUseCase.verify(
            argThat(
                (BackupCodeChallengeVerifyCommand command) ->
                    "challenge-1".equals(command.challengeId())
                        && "ABCD-EFGH".equals(command.backupCode()))))
        .thenReturn(
            LoginResult.success(
                "access-token",
                "refresh-token",
                "Bearer",
                Instant.parse("2026-04-20T11:00:00Z"),
                Instant.parse("2026-05-04T10:30:00Z"),
                7L));

    mockMvc
        .perform(
            post("/api/v1/auth/mfa/backup-codes/challenge/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "challengeId": "challenge-1",
                      "backupCode": "ABCD-EFGH"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(jsonPath("$.accessToken").value("access-token"))
        .andExpect(jsonPath("$.refreshToken").value("refresh-token"));
  }
}
