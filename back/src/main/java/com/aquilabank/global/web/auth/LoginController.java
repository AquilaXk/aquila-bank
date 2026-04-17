package com.aquilabank.global.web.auth;

import com.aquilabank.domain.auth.model.LoginCommand;
import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.model.LogoutCommand;
import com.aquilabank.domain.auth.model.RefreshTokenCommand;
import com.aquilabank.domain.auth.usecase.LoginUseCase;
import com.aquilabank.domain.auth.usecase.LogoutUseCase;
import com.aquilabank.domain.auth.usecase.RefreshTokenUseCase;
import com.aquilabank.global.security.AuthenticatedAccountPrincipal;
import com.aquilabank.global.security.AuthenticatedRequestPrincipal;
import com.aquilabank.global.security.AuthenticatedUserPrincipal;
import com.aquilabank.global.web.security.CurrentAuthenticatedPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** public auth session 생성, 재발급, 종료 API를 노출합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {

  private final LoginUseCase loginUseCase;
  private final RefreshTokenUseCase refreshTokenUseCase;
  private final LogoutUseCase logoutUseCase;

  public LoginController(
      LoginUseCase loginUseCase,
      RefreshTokenUseCase refreshTokenUseCase,
      LogoutUseCase logoutUseCase) {
    this.loginUseCase = loginUseCase;
    this.refreshTokenUseCase = refreshTokenUseCase;
    this.logoutUseCase = logoutUseCase;
  }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    LoginResult result =
        loginUseCase.login(new LoginCommand(request.loginId(), request.password()));
    return LoginResponse.from(result);
  }

  @PostMapping("/refresh")
  public LoginResponse refresh(@Valid @RequestBody RefreshRequest request) {
    LoginResult result =
        refreshTokenUseCase.refresh(new RefreshTokenCommand(request.refreshToken()));
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

  private long resolveUserId(AuthenticatedRequestPrincipal principal) {
    if (principal instanceof AuthenticatedUserPrincipal userPrincipal) {
      return userPrincipal.userId();
    }
    if (principal instanceof AuthenticatedAccountPrincipal) {
      throw new ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN, "user authentication is required");
    }
    throw new ResponseStatusException(
        org.springframework.http.HttpStatus.UNAUTHORIZED, "authentication required");
  }
}
