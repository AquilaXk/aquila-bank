package com.aquilabank.global.web.auth;

import com.aquilabank.domain.auth.model.AuthSessionList;
import com.aquilabank.domain.auth.model.AuthSessionListQuery;
import com.aquilabank.domain.auth.model.AuthSessionRevokeAllCommand;
import com.aquilabank.domain.auth.model.AuthSessionRevokeCommand;
import com.aquilabank.domain.auth.model.AuthSessionSummary;
import com.aquilabank.domain.auth.model.BackupCodeChallengeVerifyCommand;
import com.aquilabank.domain.auth.model.BackupCodeGenerateCommand;
import com.aquilabank.domain.auth.model.BackupCodeIssueResult;
import com.aquilabank.domain.auth.model.LoginCommand;
import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.model.LogoutCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryConfirmCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryRequestCommand;
import com.aquilabank.domain.auth.model.PasswordRecoveryRequestResult;
import com.aquilabank.domain.auth.model.PasswordResetCommand;
import com.aquilabank.domain.auth.model.RefreshTokenCommand;
import com.aquilabank.domain.auth.model.TotpChallengeVerifyCommand;
import com.aquilabank.domain.auth.model.TotpDisableCommand;
import com.aquilabank.domain.auth.model.TotpEnrollmentStartCommand;
import com.aquilabank.domain.auth.model.TotpEnrollmentStartResult;
import com.aquilabank.domain.auth.model.TotpEnrollmentVerifyCommand;
import com.aquilabank.domain.auth.model.TotpEnrollmentVerifyResult;
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
import com.aquilabank.global.security.AuthenticatedAccountPrincipal;
import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.security.LoginThrottleGuard;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** public auth session 생성, 조회, 재발급, 종료 API를 노출합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {

  private static final String PASSWORD_RECOVERY_HANDOFF_REQUEST_ID_HEADER =
      "X-Password-Recovery-Request-Id";

  private final LoginUseCase loginUseCase;
  private final AuthSessionListUseCase authSessionListUseCase;
  private final AuthSessionRevokeUseCase authSessionRevokeUseCase;
  private final AuthSessionRevokeAllUseCase authSessionRevokeAllUseCase;
  private final RefreshTokenUseCase refreshTokenUseCase;
  private final TotpEnrollmentUseCase totpEnrollmentUseCase;
  private final TotpChallengeVerifyUseCase totpChallengeVerifyUseCase;
  private final TotpDisableUseCase totpDisableUseCase;
  private final BackupCodeGenerateUseCase backupCodeGenerateUseCase;
  private final BackupCodeChallengeVerifyUseCase backupCodeChallengeVerifyUseCase;
  private final LogoutUseCase logoutUseCase;
  private final PasswordResetUseCase passwordResetUseCase;
  private final PasswordRecoveryRequestUseCase passwordRecoveryRequestUseCase;
  private final PasswordRecoveryConfirmUseCase passwordRecoveryConfirmUseCase;
  private final AuthSessionMetadataResolver authSessionMetadataResolver;
  private final LoginThrottleGuard loginThrottleGuard;

  public LoginController(
      LoginUseCase loginUseCase,
      AuthSessionListUseCase authSessionListUseCase,
      AuthSessionRevokeUseCase authSessionRevokeUseCase,
      AuthSessionRevokeAllUseCase authSessionRevokeAllUseCase,
      RefreshTokenUseCase refreshTokenUseCase,
      TotpEnrollmentUseCase totpEnrollmentUseCase,
      TotpChallengeVerifyUseCase totpChallengeVerifyUseCase,
      TotpDisableUseCase totpDisableUseCase,
      BackupCodeGenerateUseCase backupCodeGenerateUseCase,
      BackupCodeChallengeVerifyUseCase backupCodeChallengeVerifyUseCase,
      LogoutUseCase logoutUseCase,
      PasswordResetUseCase passwordResetUseCase,
      PasswordRecoveryRequestUseCase passwordRecoveryRequestUseCase,
      PasswordRecoveryConfirmUseCase passwordRecoveryConfirmUseCase,
      AuthSessionMetadataResolver authSessionMetadataResolver,
      LoginThrottleGuard loginThrottleGuard) {
    this.loginUseCase = loginUseCase;
    this.authSessionListUseCase = authSessionListUseCase;
    this.authSessionRevokeUseCase = authSessionRevokeUseCase;
    this.authSessionRevokeAllUseCase = authSessionRevokeAllUseCase;
    this.refreshTokenUseCase = refreshTokenUseCase;
    this.totpEnrollmentUseCase = totpEnrollmentUseCase;
    this.totpChallengeVerifyUseCase = totpChallengeVerifyUseCase;
    this.totpDisableUseCase = totpDisableUseCase;
    this.backupCodeGenerateUseCase = backupCodeGenerateUseCase;
    this.backupCodeChallengeVerifyUseCase = backupCodeChallengeVerifyUseCase;
    this.logoutUseCase = logoutUseCase;
    this.passwordResetUseCase = passwordResetUseCase;
    this.passwordRecoveryRequestUseCase = passwordRecoveryRequestUseCase;
    this.passwordRecoveryConfirmUseCase = passwordRecoveryConfirmUseCase;
    this.authSessionMetadataResolver = authSessionMetadataResolver;
    this.loginThrottleGuard = loginThrottleGuard;
  }

  @PostMapping("/login")
  public LoginResponse login(
      HttpServletRequest httpServletRequest, @Valid @RequestBody LoginRequest request) {
    var sessionClientMetadata = authSessionMetadataResolver.resolve(httpServletRequest);
    loginThrottleGuard.check(sessionClientMetadata.ipAddress());
    LoginResult result =
        loginUseCase.login(
            new LoginCommand(request.loginId(), request.password(), sessionClientMetadata));
    return LoginResponse.from(result);
  }

  @PostMapping("/mfa/totp/enroll")
  public TotpEnrollmentStartResponse startTotpEnrollment(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal) {
    AuthenticatedUserPrincipal userPrincipal = requireUserPrincipal(principal);
    TotpEnrollmentStartResult result =
        totpEnrollmentUseCase.start(
            new TotpEnrollmentStartCommand(userPrincipal.userId(), userPrincipal.subject()));
    return TotpEnrollmentStartResponse.from(result);
  }

  @PostMapping("/mfa/totp/enroll/verify")
  public TotpEnrollmentVerifyResponse verifyTotpEnrollment(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @Valid @RequestBody TotpCodeRequest request) {
    AuthenticatedUserPrincipal userPrincipal = requireUserPrincipal(principal);
    TotpEnrollmentVerifyResult result =
        totpEnrollmentUseCase.verify(
            new TotpEnrollmentVerifyCommand(userPrincipal.userId(), request.totpCode()));
    return TotpEnrollmentVerifyResponse.from(result);
  }

  @PostMapping("/mfa/totp/challenge/verify")
  public LoginResponse verifyTotpChallenge(@Valid @RequestBody TotpChallengeVerifyRequest request) {
    LoginResult result =
        totpChallengeVerifyUseCase.verify(
            new TotpChallengeVerifyCommand(request.challengeId(), request.totpCode()));
    return LoginResponse.from(result);
  }

  @PostMapping("/mfa/totp/disable")
  public ResponseEntity<Void> disableTotp(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @Valid @RequestBody TotpCodeRequest request) {
    AuthenticatedUserPrincipal userPrincipal = requireUserPrincipal(principal);
    totpDisableUseCase.disable(new TotpDisableCommand(userPrincipal.userId(), request.totpCode()));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/mfa/backup-codes")
  public BackupCodeIssueResponse issueBackupCodes(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @Valid @RequestBody TotpCodeRequest request) {
    AuthenticatedUserPrincipal userPrincipal = requireUserPrincipal(principal);
    BackupCodeIssueResult result =
        backupCodeGenerateUseCase.issue(
            new BackupCodeGenerateCommand(userPrincipal.userId(), request.totpCode()));
    return BackupCodeIssueResponse.from(result);
  }

  @PostMapping("/mfa/backup-codes/challenge/verify")
  public LoginResponse verifyBackupCodeChallenge(
      @Valid @RequestBody BackupCodeChallengeVerifyRequest request) {
    LoginResult result =
        backupCodeChallengeVerifyUseCase.verify(
            new BackupCodeChallengeVerifyCommand(request.challengeId(), request.backupCode()));
    return LoginResponse.from(result);
  }

  @GetMapping("/sessions")
  public AuthSessionListResponse getSessions(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "size must be at least 1") @Max(value = 50, message = "size must be 50 or less") int size) {
    AuthSessionList result =
        authSessionListUseCase.get(new AuthSessionListQuery(resolveUserId(principal), size));
    return AuthSessionListResponse.from(result);
  }

  @DeleteMapping("/sessions/{sessionId}")
  public ResponseEntity<Void> revokeSession(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @PathVariable @jakarta.validation.constraints.Positive(message = "sessionId must be positive") long sessionId) {
    authSessionRevokeUseCase.revoke(
        new AuthSessionRevokeCommand(resolveUserId(principal), sessionId));
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/sessions")
  public ResponseEntity<Void> revokeAllSessions(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal) {
    authSessionRevokeAllUseCase.revokeAll(
        new AuthSessionRevokeAllCommand(resolveUserId(principal)));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/refresh")
  public LoginResponse refresh(
      HttpServletRequest httpServletRequest, @Valid @RequestBody RefreshRequest request) {
    LoginResult result =
        refreshTokenUseCase.refresh(
            new RefreshTokenCommand(
                request.refreshToken(), authSessionMetadataResolver.resolve(httpServletRequest)));
    return LoginResponse.from(result);
  }

  @PostMapping("/logout")
  public ResponseEntity<Void> logout(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @Valid @RequestBody LogoutRequest request) {
    logoutUseCase.logout(new LogoutCommand(resolveUserId(principal), request.refreshToken()));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/password-reset")
  public ResponseEntity<Void> resetPassword(
      @CurrentAuthenticatedPrincipal AuthenticatedRequestPrincipal principal,
      @Valid @RequestBody PasswordResetRequest request) {
    passwordResetUseCase.reset(
        new PasswordResetCommand(
            resolveUserId(principal), request.currentPassword(), request.newPassword()));
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/password-recovery/request")
  public ResponseEntity<Void> requestPasswordRecovery(
      @Valid @RequestBody PasswordRecoveryRequest request) {
    PasswordRecoveryRequestResult result =
        passwordRecoveryRequestUseCase.request(
            new PasswordRecoveryRequestCommand(request.loginId()));
    return ResponseEntity.noContent()
        // trace용 X-Request-Id와 분리된 handoff requestId만 별도 header로 반환합니다.
        .header(PASSWORD_RECOVERY_HANDOFF_REQUEST_ID_HEADER, result.handoffRequestId())
        .build();
  }

  @PostMapping("/password-recovery/confirm")
  public ResponseEntity<Void> confirmPasswordRecovery(
      @Valid @RequestBody PasswordRecoveryConfirmRequest request) {
    passwordRecoveryConfirmUseCase.confirm(
        new PasswordRecoveryConfirmCommand(request.recoveryToken(), request.newPassword()));
    return ResponseEntity.noContent().build();
  }

  /** 로그인 요청 body */
  public record LoginRequest(
      @NotBlank(message = "loginId is required") @Size(max = 80, message = "loginId must be 80 characters or less") String loginId,
      @NotBlank(message = "password is required") @Size(max = 120, message = "password must be 120 characters or less") String password) {}

  /** refresh token 재발급 요청 body */
  public record RefreshRequest(
      @NotBlank(message = "refreshToken is required") @Size(max = 160, message = "refreshToken must be 160 characters or less") String refreshToken) {}

  /** logout 요청 body */
  public record LogoutRequest(
      @NotBlank(message = "refreshToken is required") @Size(max = 160, message = "refreshToken must be 160 characters or less") String refreshToken) {}

  /** password reset 요청 body */
  public record PasswordResetRequest(
      @NotBlank(message = "currentPassword is required") @Size(max = 120, message = "currentPassword must be 120 characters or less") String currentPassword,
      @NotBlank(message = "newPassword is required") @Size(max = 120, message = "newPassword must be 120 characters or less") String newPassword) {}

  /** password recovery 요청 body */
  public record PasswordRecoveryRequest(
      @NotBlank(message = "loginId is required") @Size(max = 80, message = "loginId must be 80 characters or less") String loginId) {}

  /** password recovery 확정 요청 body */
  public record PasswordRecoveryConfirmRequest(
      @NotBlank(message = "recoveryToken is required") @Size(max = 160, message = "recoveryToken must be 160 characters or less") String recoveryToken,
      @NotBlank(message = "newPassword is required") @Size(max = 120, message = "newPassword must be 120 characters or less") String newPassword) {}

  /** TOTP code 입력 body */
  public record TotpCodeRequest(
      @NotBlank(message = "totpCode is required") @Pattern(regexp = "\\d{6}", message = "totpCode must be 6 digits") String totpCode) {}

  /** MFA challenge verify 요청 body */
  public record TotpChallengeVerifyRequest(
      @NotBlank(message = "challengeId is required") @Size(max = 64, message = "challengeId must be 64 characters or less") String challengeId,
      @NotBlank(message = "totpCode is required") @Pattern(regexp = "\\d{6}", message = "totpCode must be 6 digits") String totpCode) {}

  /** backup code challenge verify 요청 body */
  public record BackupCodeChallengeVerifyRequest(
      @NotBlank(message = "challengeId is required") @Size(max = 64, message = "challengeId must be 64 characters or less") String challengeId,
      @NotBlank(message = "backupCode is required") @Size(max = 16, message = "backupCode must be 16 characters or less") String backupCode) {}

  /** access/refresh token 발급 응답 */
  public record LoginResponse(
      String status,
      String accessToken,
      String refreshToken,
      String tokenType,
      Instant expiresAt,
      Instant refreshExpiresAt,
      Long userId,
      String challengeId,
      String challengeType,
      Instant challengeExpiresAt) {

    private static LoginResponse from(LoginResult result) {
      return new LoginResponse(
          result.status().name(),
          result.accessToken(),
          result.refreshToken(),
          result.tokenType(),
          result.expiresAt(),
          result.refreshExpiresAt(),
          result.userId(),
          result.challengeId(),
          result.challengeType() == null ? null : result.challengeType().name(),
          result.challengeExpiresAt());
    }
  }

  /** TOTP enrollment 시작 응답 */
  public record TotpEnrollmentStartResponse(
      String status, String secretKey, String otpauthUri, Instant expiresAt) {

    private static TotpEnrollmentStartResponse from(TotpEnrollmentStartResult result) {
      return new TotpEnrollmentStartResponse(
          "PENDING", result.secretKey(), result.otpauthUri(), result.expiresAt());
    }
  }

  /** TOTP enrollment 활성화 응답 */
  public record TotpEnrollmentVerifyResponse(String status, Instant verifiedAt) {

    private static TotpEnrollmentVerifyResponse from(TotpEnrollmentVerifyResult result) {
      return new TotpEnrollmentVerifyResponse(
          result.credentialStatus().name(), result.verifiedAt());
    }
  }

  /** backup code 발급 응답 */
  public record BackupCodeIssueResponse(int codeCount, List<String> backupCodes) {

    private static BackupCodeIssueResponse from(BackupCodeIssueResult result) {
      return new BackupCodeIssueResponse(result.codeCount(), result.backupCodes());
    }
  }

  /** 현재 user refresh token session 목록 응답 */
  public record AuthSessionListResponse(List<AuthSessionItemResponse> items) {

    private static AuthSessionListResponse from(AuthSessionList result) {
      return new AuthSessionListResponse(
          result.items().stream().map(AuthSessionItemResponse::from).toList());
    }
  }

  /** 현재 user refresh token session 목록 item 응답 */
  public record AuthSessionItemResponse(
      long sessionId,
      String sessionStatus,
      Instant expiresAt,
      Instant lastUsedAt,
      Instant createdAt,
      String deviceName,
      String ipAddress) {

    private static AuthSessionItemResponse from(AuthSessionSummary summary) {
      return new AuthSessionItemResponse(
          summary.sessionId(),
          summary.sessionStatus().name(),
          summary.expiresAt(),
          summary.lastUsedAt(),
          summary.createdAt(),
          summary.deviceName(),
          summary.ipAddress());
    }
  }

  private long resolveUserId(AuthenticatedRequestPrincipal principal) {
    return requireUserPrincipal(principal).userId();
  }

  private AuthenticatedUserPrincipal requireUserPrincipal(AuthenticatedRequestPrincipal principal) {
    if (principal instanceof AuthenticatedUserPrincipal userPrincipal) {
      return userPrincipal;
    }
    if (principal instanceof AuthenticatedAccountPrincipal) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "user authentication is required");
    }
    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
  }
}
