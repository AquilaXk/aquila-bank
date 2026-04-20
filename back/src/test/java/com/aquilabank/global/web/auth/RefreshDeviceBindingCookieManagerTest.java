package com.aquilabank.global.web.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.global.security.SecurityJwtProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

class RefreshDeviceBindingCookieManagerTest {

  @Test
  void readsBindingCookieFromRequest() {
    RefreshDeviceBindingCookieManager manager =
        new RefreshDeviceBindingCookieManager(
            new SecurityJwtProperties("secret", "issuer", 900L, 1_209_600L, "ab_refresh_device"));
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setCookies(new Cookie("ab_refresh_device", "binding-token"));

    assertThat(manager.resolve(request)).isEqualTo("binding-token");
  }

  @Test
  void addsScopedHttpOnlyCookieAndClearCookie() {
    RefreshDeviceBindingCookieManager manager =
        new RefreshDeviceBindingCookieManager(
            new SecurityJwtProperties("secret", "issuer", 900L, 1_209_600L, "ab_refresh_device"));
    HttpHeaders headers = new HttpHeaders();

    manager.addBindingCookie(headers, "binding-token");
    manager.addClearCookie(headers);

    String setCookieHeaders = String.join("\n", headers.getOrEmpty(HttpHeaders.SET_COOKIE));

    assertThat(setCookieHeaders).contains("ab_refresh_device=binding-token");
    assertThat(setCookieHeaders).contains("ab_refresh_device=");
    assertThat(setCookieHeaders).contains("HttpOnly");
    assertThat(setCookieHeaders).contains("Secure");
    assertThat(setCookieHeaders).contains("SameSite=Lax");
    assertThat(setCookieHeaders).contains("Path=/api/v1/auth/refresh");
    assertThat(setCookieHeaders).contains("Max-Age=1209600");
    assertThat(setCookieHeaders).contains("Max-Age=0");
  }
}
