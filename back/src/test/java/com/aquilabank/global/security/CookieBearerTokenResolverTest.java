package com.aquilabank.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class CookieBearerTokenResolverTest {

  private final CookieBearerTokenResolver resolver =
      new CookieBearerTokenResolver("ab_access_token");

  @Test
  void rejectsBlankAccessTokenCookieName() {
    assertThatThrownBy(() -> new CookieBearerTokenResolver(" "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("accessTokenCookieName is required");
  }

  @Test
  void resolvesAuthorizationHeaderFirst() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("Authorization", "Bearer header-token");
    request.setCookies(new Cookie("ab_access_token", "cookie-token"));

    assertThat(resolver.resolve(request)).isEqualTo("header-token");
  }

  @Test
  void resolvesAccessTokenCookieWhenAuthorizationHeaderIsMissing() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("ab_access_token", "cookie-token"));

    assertThat(resolver.resolve(request)).isEqualTo("cookie-token");
  }

  @Test
  void rejectsAccessTokenQueryParameter() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setParameter("accessToken", "query-token");

    assertThat(resolver.resolve(request)).isNull();
  }
}
