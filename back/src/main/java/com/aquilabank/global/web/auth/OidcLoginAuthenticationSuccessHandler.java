package com.aquilabank.global.web.auth;

import com.aquilabank.domain.auth.exception.InvalidCredentialsException;
import com.aquilabank.domain.auth.model.ExternalOidcLoginCommand;
import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.usecase.ExternalOidcLoginUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

/** OIDC 인증 성공을 내부 token session 발급 응답으로 교환합니다. */
@Component
public class OidcLoginAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

  private final ExternalOidcLoginUseCase externalOidcLoginUseCase;
  private final AuthSessionMetadataResolver authSessionMetadataResolver;
  private final RefreshDeviceBindingCookieManager refreshDeviceBindingCookieManager;
  private final ObjectMapper objectMapper;

  public OidcLoginAuthenticationSuccessHandler(
      ExternalOidcLoginUseCase externalOidcLoginUseCase,
      AuthSessionMetadataResolver authSessionMetadataResolver,
      RefreshDeviceBindingCookieManager refreshDeviceBindingCookieManager,
      ObjectMapper objectMapper) {
    this.externalOidcLoginUseCase = externalOidcLoginUseCase;
    this.authSessionMetadataResolver = authSessionMetadataResolver;
    this.refreshDeviceBindingCookieManager = refreshDeviceBindingCookieManager;
    this.objectMapper = objectMapper;
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request, HttpServletResponse response, Authentication authentication)
      throws IOException, ServletException {
    if (!(authentication instanceof OAuth2AuthenticationToken authenticationToken)
        || !(authenticationToken.getPrincipal() instanceof OidcUser oidcUser)) {
      writeUnauthorized(response);
      return;
    }

    String subject = oidcUser.getSubject();
    if (subject == null || subject.isBlank()) {
      writeUnauthorized(response);
      return;
    }

    try {
      LoginResult result =
          externalOidcLoginUseCase.login(
              new ExternalOidcLoginCommand(
                  authenticationToken.getAuthorizedClientRegistrationId(),
                  subject,
                  authSessionMetadataResolver.resolve(request)));
      writeSuccess(response, result);
    } catch (InvalidCredentialsException ex) {
      writeUnauthorized(response);
    }
  }

  private void writeSuccess(HttpServletResponse response, LoginResult result) throws IOException {
    HttpHeaders headers = new HttpHeaders();
    if (result.refreshDeviceBindingToken() != null) {
      refreshDeviceBindingCookieManager.addBindingCookie(
          headers, result.refreshDeviceBindingToken());
    }
    headers.forEach((name, values) -> values.forEach(value -> response.addHeader(name, value)));
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), LoginResponse.from(result));
  }

  private void writeUnauthorized(HttpServletResponse response) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(
        response.getOutputStream(), new ErrorResponse("UNAUTHORIZED", "oidc login failed"));
  }

  private record LoginResponse(
      String status,
      String accessToken,
      String refreshToken,
      String tokenType,
      Instant expiresAt,
      Instant refreshExpiresAt,
      Long userId) {

    private static LoginResponse from(LoginResult result) {
      return new LoginResponse(
          result.status().name(),
          result.accessToken(),
          result.refreshToken(),
          result.tokenType(),
          result.expiresAt(),
          result.refreshExpiresAt(),
          result.userId());
    }
  }

  private record ErrorResponse(String status, String message) {}
}
