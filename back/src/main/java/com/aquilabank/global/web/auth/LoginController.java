package com.aquilabank.global.web.auth;

import com.aquilabank.domain.auth.model.LoginCommand;
import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.usecase.LoginUseCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 최소 login API로 실제 bearer JWT 발급 경로를 노출합니다. */
@Validated
@RestController
@RequestMapping("/api/v1/auth")
public class LoginController {

  private final LoginUseCase loginUseCase;

  public LoginController(LoginUseCase loginUseCase) {
    this.loginUseCase = loginUseCase;
  }

  @PostMapping("/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    LoginResult result =
        loginUseCase.login(new LoginCommand(request.loginId(), request.password()));
    return LoginResponse.from(result);
  }

  /** 로그인 요청 body */
  public record LoginRequest(
      @NotBlank(message = "loginId is required") @Size(max = 80, message = "loginId must be 80 characters or less") String loginId,
      @NotBlank(message = "password is required") @Size(max = 120, message = "password must be 120 characters or less") String password) {}

  /** access token 발급 응답 */
  public record LoginResponse(
      String accessToken, String tokenType, java.time.Instant expiresAt, long userId) {

    private static LoginResponse from(LoginResult result) {
      return new LoginResponse(
          result.accessToken(), result.tokenType(), result.expiresAt(), result.userId());
    }
  }
}
