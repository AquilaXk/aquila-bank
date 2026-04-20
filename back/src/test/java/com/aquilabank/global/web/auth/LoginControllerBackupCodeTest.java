package com.aquilabank.global.web.auth;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.auth.model.BackupCodeGenerateCommand;
import com.aquilabank.domain.auth.model.BackupCodeIssueResult;
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
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.security.LoginThrottleGuard;
import com.aquilabank.global.security.SecurityJwtProperties;
import com.aquilabank.global.web.ApiExceptionHandler;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipalArgumentResolver;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LoginControllerBackupCodeTest {

  private BackupCodeGenerateUseCase backupCodeGenerateUseCase;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    backupCodeGenerateUseCase = mock(BackupCodeGenerateUseCase.class);
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
                    backupCodeGenerateUseCase,
                    mock(BackupCodeChallengeVerifyUseCase.class),
                    mock(LogoutUseCase.class),
                    mock(PasswordResetUseCase.class),
                    mock(PasswordRecoveryRequestUseCase.class),
                    mock(PasswordRecoveryConfirmUseCase.class),
                    mock(AuthSessionMetadataResolver.class),
                    mock(LoginThrottleGuard.class),
                    new RefreshDeviceBindingCookieManager(
                        new SecurityJwtProperties(
                            "secret", "issuer", 900L, 1_209_600L, "ab_refresh_device"))))
            .setControllerAdvice(new ApiExceptionHandler())
            .setCustomArgumentResolvers(new CurrentAuthenticatedPrincipalArgumentResolver())
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void issuesBackupCodesForAuthenticatedUser() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUserPrincipal(7L, "alice"), null, List.of()));
    when(backupCodeGenerateUseCase.issue(
            argThat(
                (BackupCodeGenerateCommand command) ->
                    command.userId() == 7L && "123456".equals(command.totpCode()))))
        .thenReturn(new BackupCodeIssueResult(List.of("ABCD-EFGH", "JKLM-NPQR"), 2));

    mockMvc
        .perform(
            post("/api/v1/auth/mfa/backup-codes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "totpCode": "123456"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.codeCount").value(2))
        .andExpect(jsonPath("$.backupCodes[0]").value("ABCD-EFGH"))
        .andExpect(jsonPath("$.backupCodes[1]").value("JKLM-NPQR"));
  }
}
