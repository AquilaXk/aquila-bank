package com.aquilabank.global.web.auth;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aquilabank.domain.auth.model.AuthSessionClientMetadata;
import com.aquilabank.domain.auth.model.LoginCommand;
import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.model.RefreshTokenCommand;
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
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.security.LoginThrottleGuard;
import com.aquilabank.global.security.SecurityAuthCookieProperties;
import com.aquilabank.global.security.SecurityJwtProperties;
import com.aquilabank.global.security.SecurityRememberDeviceProperties;
import com.aquilabank.global.web.ApiExceptionHandler;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipalArgumentResolver;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LoginControllerRefreshDeviceBindingTest {

  private static final String COOKIE_NAME = "ab_refresh_device";
  private static final String ACCESS_COOKIE_NAME = "ab_access_token";
  private static final String REFRESH_COOKIE_NAME = "ab_refresh_token";

  private LoginUseCase loginUseCase;
  private RefreshTokenUseCase refreshTokenUseCase;
  private TotpChallengeVerifyUseCase totpChallengeVerifyUseCase;
  private AuthSessionRevokeAllUseCase authSessionRevokeAllUseCase;
  private TotpDisableUseCase totpDisableUseCase;
  private LogoutUseCase logoutUseCase;
  private PasswordResetUseCase passwordResetUseCase;
  private AuthSessionMetadataResolver authSessionMetadataResolver;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    loginUseCase = mock(LoginUseCase.class);
    refreshTokenUseCase = mock(RefreshTokenUseCase.class);
    totpChallengeVerifyUseCase = mock(TotpChallengeVerifyUseCase.class);
    authSessionRevokeAllUseCase = mock(AuthSessionRevokeAllUseCase.class);
    totpDisableUseCase = mock(TotpDisableUseCase.class);
    logoutUseCase = mock(LogoutUseCase.class);
    passwordResetUseCase = mock(PasswordResetUseCase.class);
    authSessionMetadataResolver = mock(AuthSessionMetadataResolver.class);
    when(authSessionMetadataResolver.resolve(any()))
        .thenReturn(new AuthSessionClientMetadata("Windows / Chrome", "203.0.113.10"));

    mockMvc =
        MockMvcBuilders.standaloneSetup(
                new LoginController(
                    loginUseCase,
                    mock(AuthSessionListUseCase.class),
                    mock(AuthSessionRevokeUseCase.class),
                    authSessionRevokeAllUseCase,
                    refreshTokenUseCase,
                    mock(TotpEnrollmentUseCase.class),
                    totpChallengeVerifyUseCase,
                    totpDisableUseCase,
                    mock(BackupCodeGenerateUseCase.class),
                    mock(BackupCodeChallengeVerifyUseCase.class),
                    logoutUseCase,
                    passwordResetUseCase,
                    mock(PasswordRecoveryRequestUseCase.class),
                    mock(PasswordRecoveryConfirmUseCase.class),
                    authSessionMetadataResolver,
                    mock(LoginThrottleGuard.class),
                    new RememberDeviceCookieManager(
                        new SecurityRememberDeviceProperties("ab_mfa_remember_device", 2_592_000L)),
                    new RefreshDeviceBindingCookieManager(
                        new SecurityJwtProperties(
                            "secret", "issuer", 900L, 1_209_600L, COOKIE_NAME)),
                    new AuthSessionCookieManager(
                        new SecurityJwtProperties(
                            "secret", "issuer", 900L, 1_209_600L, COOKIE_NAME),
                        new SecurityAuthCookieProperties(
                            ACCESS_COOKIE_NAME, REFRESH_COOKIE_NAME, false))))
            .setControllerAdvice(new ApiExceptionHandler())
            .setCustomArgumentResolvers(new CurrentAuthenticatedPrincipalArgumentResolver())
            .build();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
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
                "binding-token",
                null));

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
                .stringValues(
                    "Set-Cookie",
                    org.hamcrest.Matchers.hasItems(
                        org.hamcrest.Matchers.containsString(COOKIE_NAME + "=binding-token"),
                        org.hamcrest.Matchers.allOf(
                            org.hamcrest.Matchers.containsString(
                                ACCESS_COOKIE_NAME + "=access-token"),
                            org.hamcrest.Matchers.containsString("HttpOnly")),
                        org.hamcrest.Matchers.allOf(
                            org.hamcrest.Matchers.containsString(
                                REFRESH_COOKIE_NAME + "=refresh-token"),
                            org.hamcrest.Matchers.containsString("HttpOnly")))));
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
                "binding-token",
                null));

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
                .stringValues(
                    "Set-Cookie",
                    org.hamcrest.Matchers.hasItems(
                        org.hamcrest.Matchers.containsString(COOKIE_NAME + "=binding-token"),
                        org.hamcrest.Matchers.containsString(ACCESS_COOKIE_NAME + "=access-token"),
                        org.hamcrest.Matchers.containsString(
                            REFRESH_COOKIE_NAME + "=refresh-token"))));
  }

  @Test
  void refreshReadsBindingCookieAndSetsRotatedBindingCookieOnSuccess() throws Exception {
    when(refreshTokenUseCase.refresh(
            argThat(
                (RefreshTokenCommand command) ->
                    "refresh-token".equals(command.refreshToken())
                        && "binding-token".equals(command.refreshDeviceBindingToken()))))
        .thenReturn(
            LoginResult.success(
                "access-token",
                "next-refresh-token",
                "Bearer",
                Instant.parse("2026-04-20T12:00:00Z"),
                Instant.parse("2026-05-04T11:45:00Z"),
                7L,
                "next-binding-token",
                null));

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .cookie(new Cookie(COOKIE_NAME, "binding-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "refreshToken": "refresh-token"
                    }
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"))
        .andExpect(
            header()
                .stringValues(
                    "Set-Cookie",
                    org.hamcrest.Matchers.hasItems(
                        org.hamcrest.Matchers.containsString(COOKIE_NAME + "=next-binding-token"),
                        org.hamcrest.Matchers.containsString(ACCESS_COOKIE_NAME + "=access-token"),
                        org.hamcrest.Matchers.containsString(
                            REFRESH_COOKIE_NAME + "=next-refresh-token"))));
  }

  @Test
  void refreshReadsRefreshTokenCookieWhenBodyOmitsRefreshToken() throws Exception {
    when(refreshTokenUseCase.refresh(
            argThat(
                (RefreshTokenCommand command) ->
                    "refresh-token".equals(command.refreshToken())
                        && "binding-token".equals(command.refreshDeviceBindingToken()))))
        .thenReturn(
            LoginResult.success(
                "access-token",
                "next-refresh-token",
                "Bearer",
                Instant.parse("2026-04-20T12:00:00Z"),
                Instant.parse("2026-05-04T11:45:00Z"),
                7L,
                "next-binding-token",
                null));

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .cookie(new Cookie(COOKIE_NAME, "binding-token"))
                .cookie(new Cookie(REFRESH_COOKIE_NAME, "refresh-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("SUCCESS"));
  }

  @Test
  void logoutClearsBindingCookie() throws Exception {
    authenticate();

    mockMvc
        .perform(
            post("/api/v1/auth/logout")
                .cookie(new Cookie(REFRESH_COOKIE_NAME, "refresh-token"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isNoContent())
        .andExpect(
            header()
                .stringValues(
                    "Set-Cookie",
                    org.hamcrest.Matchers.hasItems(
                        org.hamcrest.Matchers.allOf(
                            org.hamcrest.Matchers.containsString(COOKIE_NAME + "="),
                            org.hamcrest.Matchers.containsString("Max-Age=0")),
                        org.hamcrest.Matchers.allOf(
                            org.hamcrest.Matchers.containsString(ACCESS_COOKIE_NAME + "="),
                            org.hamcrest.Matchers.containsString("Max-Age=0")),
                        org.hamcrest.Matchers.allOf(
                            org.hamcrest.Matchers.containsString(REFRESH_COOKIE_NAME + "="),
                            org.hamcrest.Matchers.containsString("Max-Age=0")))));

    verify(logoutUseCase)
        .logout(
            argThat(
                command ->
                    command.userId() == 7L && "refresh-token".equals(command.refreshToken())));
  }

  @Test
  void revokeAllSessionsClearsBindingCookie() throws Exception {
    authenticate();

    mockMvc
        .perform(delete("/api/v1/auth/sessions"))
        .andExpect(status().isNoContent())
        .andExpect(
            header()
                .stringValues(
                    "Set-Cookie",
                    org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.allOf(
                            org.hamcrest.Matchers.containsString(COOKIE_NAME + "="),
                            org.hamcrest.Matchers.containsString("Max-Age=0")))));
  }

  @Test
  void passwordResetClearsBindingCookie() throws Exception {
    authenticate();

    mockMvc
        .perform(
            post("/api/v1/auth/password-reset")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "currentPassword": "password123!",
                      "newPassword": "newPassword456!"
                    }
                    """))
        .andExpect(status().isNoContent())
        .andExpect(
            header()
                .stringValues(
                    "Set-Cookie",
                    org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.allOf(
                            org.hamcrest.Matchers.containsString(COOKIE_NAME + "="),
                            org.hamcrest.Matchers.containsString("Max-Age=0")))));
  }

  @Test
  void disableTotpClearsBindingCookie() throws Exception {
    authenticate();

    mockMvc
        .perform(
            post("/api/v1/auth/mfa/totp/disable")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "totpCode": "123456"
                    }
                    """))
        .andExpect(status().isNoContent())
        .andExpect(
            header()
                .stringValues(
                    "Set-Cookie",
                    org.hamcrest.Matchers.hasItem(
                        org.hamcrest.Matchers.allOf(
                            org.hamcrest.Matchers.containsString(COOKIE_NAME + "="),
                            org.hamcrest.Matchers.containsString("Max-Age=0")))));
  }

  private void authenticate() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                new AuthenticatedUserPrincipal(7L, "alice"), null, List.of()));
  }
}
