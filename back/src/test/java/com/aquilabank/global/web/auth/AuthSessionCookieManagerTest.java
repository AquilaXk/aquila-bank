package com.aquilabank.global.web.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.global.security.SecurityAuthCookieProperties;
import com.aquilabank.global.security.SecurityJwtProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthSessionCookieManagerTest {

  private final AuthSessionCookieManager manager =
      new AuthSessionCookieManager(
          new SecurityJwtProperties("secret", "issuer", 900L, 1_209_600L, "ab_refresh_device"),
          new SecurityAuthCookieProperties("ab_access_token", "ab_refresh_token", false));

  @Test
  void returnsNullWhenRefreshCookieJarIsMissing() {
    MockHttpServletRequest request = new MockHttpServletRequest();

    assertThat(manager.resolveRefreshToken(request)).isNull();
  }

  @Test
  void returnsNullWhenRefreshCookieIsMissing() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("other_cookie", "refresh-token"));

    assertThat(manager.resolveRefreshToken(request)).isNull();
  }
}
