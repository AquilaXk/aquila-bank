package com.aquilabank.global.web.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aquilabank.domain.auth.model.AuthSessionClientMetadata;
import com.aquilabank.domain.auth.model.ExternalOidcLoginCommand;
import com.aquilabank.domain.auth.model.LoginResult;
import com.aquilabank.domain.auth.usecase.ExternalOidcLoginUseCase;
import com.aquilabank.global.security.SecurityAuthCookieProperties;
import com.aquilabank.global.security.SecurityJwtProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

class OidcLoginAuthenticationSuccessHandlerTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

  @Test
  void exchangesOidcSubjectForInternalTokenResponse() throws Exception {
    ExternalOidcLoginUseCase externalOidcLoginUseCase = mock(ExternalOidcLoginUseCase.class);
    AuthSessionMetadataResolver authSessionMetadataResolver =
        mock(AuthSessionMetadataResolver.class);
    RefreshDeviceBindingCookieManager refreshDeviceBindingCookieManager =
        new RefreshDeviceBindingCookieManager(
            new SecurityJwtProperties("secret", "issuer", 900L, 1_209_600L, "ab_refresh_device"));
    OidcLoginAuthenticationSuccessHandler handler =
        new OidcLoginAuthenticationSuccessHandler(
            externalOidcLoginUseCase,
            authSessionMetadataResolver,
            refreshDeviceBindingCookieManager,
            new AuthSessionCookieManager(
                new SecurityJwtProperties(
                    "secret", "issuer", 900L, 1_209_600L, "ab_refresh_device"),
                new SecurityAuthCookieProperties("ab_access_token", "ab_refresh_token", false)),
            OBJECT_MAPPER);
    AuthSessionClientMetadata metadata =
        new AuthSessionClientMetadata("macOS / Safari", "203.0.113.20");
    when(authSessionMetadataResolver.resolve(org.mockito.ArgumentMatchers.any()))
        .thenReturn(metadata);
    when(externalOidcLoginUseCase.login(
            argThat(
                (ExternalOidcLoginCommand command) ->
                    "google".equals(command.providerId())
                        && "oidc-subject-1".equals(command.subject())
                        && metadata.equals(command.sessionClientMetadata()))))
        .thenReturn(
            LoginResult.success(
                "access-token",
                "refresh-token",
                "Bearer",
                Instant.parse("2026-04-21T00:15:00Z"),
                Instant.parse("2026-05-05T00:00:00Z"),
                7L,
                "binding-token",
                null));

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
    MockHttpServletResponse response = new MockHttpServletResponse();

    handler.onAuthenticationSuccess(request, response, authentication("google", "oidc-subject-1"));

    JsonNode body = OBJECT_MAPPER.readTree(response.getContentAsString());
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(response.getContentType()).isEqualTo("application/json");
    assertThat(body.get("status").asText()).isEqualTo("SUCCESS");
    assertThat(body.get("accessToken").asText()).isEqualTo("access-token");
    assertThat(body.get("refreshToken").asText()).isEqualTo("refresh-token");
    assertThat(response.getHeaders("Set-Cookie"))
        .anySatisfy(cookie -> assertThat(cookie).contains("ab_access_token=access-token"))
        .anySatisfy(cookie -> assertThat(cookie).contains("ab_refresh_token=refresh-token"))
        .anySatisfy(cookie -> assertThat(cookie).contains("ab_refresh_device=binding-token"));
    verify(externalOidcLoginUseCase)
        .login(
            argThat(
                command ->
                    "google".equals(command.providerId())
                        && "oidc-subject-1".equals(command.subject())));
  }

  private OAuth2AuthenticationToken authentication(String registrationId, String subject) {
    OidcIdToken idToken =
        new OidcIdToken(
            "id-token",
            Instant.parse("2026-04-21T00:00:00Z"),
            Instant.parse("2026-04-21T00:05:00Z"),
            Map.of("sub", subject));
    OidcUser user = new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);
    return new OAuth2AuthenticationToken(user, user.getAuthorities(), registrationId);
  }
}
