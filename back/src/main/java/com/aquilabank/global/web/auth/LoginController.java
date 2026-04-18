package com.aquilabank.global.web.auth;

import com.aquilabank.domain.auth.model.AuthSessionList;
import com.aquilabank.domain.auth.model.AuthSessionListQuery;
import com.aquilabank.domain.auth.model.AuthSessionRevokeAllCommand;
import com.aquilabank.domain.auth.model.AuthSessionRevokeCommand;
import com.aquilabank.domain.auth.model.AuthSessionSummary;
import com.aquilabank.domain.auth.model.LoginCommand;
import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.model.LogoutCommand;
import com.aquilabank.domain.auth.model.RefreshTokenCommand;
import com.aquilabank.domain.auth.usecase.AuthSessionListUseCase;
import com.aquilabank.domain.auth.usecase.AuthSessionRevokeAllUseCase;
import com.aquilabank.domain.auth.usecase.AuthSessionRevokeUseCase;
import com.aquilabank.domain.auth.usecase.LoginUseCase;
import com.aquilabank.domain.auth.usecase.LogoutUseCase;
import com.aquilabank.domain.auth.usecase.RefreshTokenUseCase;
import com.aquilabank.global.security.AuthenticatedAccountPrincipal;
import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
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

  private final LoginUseCase loginUseCase;
  private final AuthSessionListUseCase authSessionListUseCase;
  private final AuthSessionRevokeUseCase authSessionRevokeUseCase;
  private final AuthSessionRevokeAllUseCase authSessionRevokeAllUseCase;
  private final RefreshTokenUseCase refreshTokenUseCase;
  private final LogoutUseCase logoutUseCase;
  private final AuthSessionMetadataResolver authSessionMetadataResolver;

  public LoginController(
      LoginUseCase loginUseCase,
      AuthSessionListUseCase authSessionListUseCase,
      AuthSessionRevokeUseCase authSessionRevokeUseCase,
      AuthSessionRevokeAllUseCase authSessionRevokeAllUseCase,
      RefreshTokenUseCase refreshTokenUseCase,
      LogoutUseCase logoutUseCase,
      AuthSessionMetadataResolver authSessionMetadataResolver) {
    this.loginUseCase = loginUseCase;
    this.authSessionListUseCase = authSessionListUseCase;
    this.authSessionRevokeUseCase = authSessionRevokeUseCase;
    this.authSessionRevokeAllUseCase = authSessionRevokeAllUseCase;
    this.refreshTokenUseCase = refreshTokenUseCase;
    this.logoutUseCase = logoutUseCase;
    this.authSessionMetadataResolver = authSessionMetadataResolver;
  }

  @PostMapping("/login")
  public LoginResponse login(
      HttpServletRequest httpServletRequest, @Valid @RequestBody LoginRequest request) {
    LoginResult result =
        loginUseCase.login(
            new LoginCommand(
                request.loginId(),
                request.password(),
                authSessionMetadataResolver.resolve(httpServletRequest)));
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

  /** access/refresh token 발급 응답 */
  public record LoginResponse(
      String accessToken,
      String refreshToken,
      String tokenType,
      Instant expiresAt,
      Instant refreshExpiresAt,
      long userId) {

    private static LoginResponse from(LoginResult result) {
      return new LoginResponse(
          result.accessToken(),
          result.refreshToken(),
          result.tokenType(),
          result.expiresAt(),
          result.refreshExpiresAt(),
          result.userId());
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
    if (principal instanceof AuthenticatedUserPrincipal userPrincipal) {
      return userPrincipal.userId();
    }
    if (principal instanceof AuthenticatedAccountPrincipal) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "user authentication is required");
    }
    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "authentication required");
  }
}
