package com.aquilabank.global.web.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.global.security.SecurityAuthCookieProperties;
import com.aquilabank.global.security.SecurityRememberDeviceProperties;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;

class RememberDeviceCookieManagerTest {

  @Test
  void resolvesRememberDeviceCookieAndHandlesMissingOrBlank() {
    RememberDeviceCookieManager manager =
        new RememberDeviceCookieManager(
            new SecurityRememberDeviceProperties("ab_mfa_remember_device", 2_592_000L));

    MockHttpServletRequest noCookies = new MockHttpServletRequest();
    assertThat(manager.resolve(noCookies)).isNull();

    MockHttpServletRequest blankCookie = new MockHttpServletRequest();
    blankCookie.setCookies(new Cookie("ab_mfa_remember_device", " "));
    assertThat(manager.resolve(blankCookie)).isNull();

    MockHttpServletRequest otherCookie = new MockHttpServletRequest();
    otherCookie.setCookies(
        new Cookie("other", "value"), new Cookie("ab_mfa_remember_device", "tok"));
    assertThat(manager.resolve(otherCookie)).isEqualTo("tok");
  }

  @Test
  void setsAndClearsRememberDeviceCookieConsistentWithSecurityAuthCookieProperties() {
    RememberDeviceCookieManager insecureManager =
        new RememberDeviceCookieManager(
            new SecurityRememberDeviceProperties("ab_mfa_remember_device", 2_592_000L),
            new SecurityAuthCookieProperties("ab_access_token", "ab_refresh_token", false));
    HttpHeaders insecureHeaders = new HttpHeaders();
    insecureManager.addRememberDeviceCookie(insecureHeaders, "tok-1");
    insecureManager.addClearCookie(insecureHeaders);
    String insecureJoined = String.join("\n", insecureHeaders.getOrEmpty(HttpHeaders.SET_COOKIE));
    assertThat(insecureJoined).contains("ab_mfa_remember_device=tok-1");
    assertThat(insecureJoined).doesNotContain("Secure");

    RememberDeviceCookieManager secureManager =
        new RememberDeviceCookieManager(
            new SecurityRememberDeviceProperties("ab_mfa_remember_device", 2_592_000L));
    HttpHeaders secureHeaders = new HttpHeaders();
    secureManager.addRememberDeviceCookie(secureHeaders, "tok-2");
    secureManager.addClearCookie(secureHeaders);
    String secureJoined = String.join("\n", secureHeaders.getOrEmpty(HttpHeaders.SET_COOKIE));
    assertThat(secureJoined).contains("ab_mfa_remember_device=tok-2");
    assertThat(secureJoined).contains("HttpOnly");
    assertThat(secureJoined).contains("Secure");
    assertThat(secureJoined).contains("SameSite=Lax");
    assertThat(secureJoined).contains("Max-Age=2592000");
    assertThat(secureJoined).contains("Max-Age=0");
  }
}
