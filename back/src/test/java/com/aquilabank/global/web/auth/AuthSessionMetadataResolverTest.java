package com.aquilabank.global.web.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.aquilabank.domain.auth.model.AuthSessionClientMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class AuthSessionMetadataResolverTest {

  private final AuthSessionMetadataResolver resolver = new AuthSessionMetadataResolver();

  @Test
  void honorsForwardedHeadersWhenRemoteAddrIsTrustedProxyOrBlank() {
    String[] trustedProxies = {
      null,
      " ",
      "127.0.0.1",
      "::1",
      "0:0:0:0:0:0:0:1",
      "localhost",
      "10.10.1.5",
      "172.16.0.1",
      "172.31.255.254",
      "192.168.10.20",
      "fc00::1",
      "fd12:3456:789a::1",
      "fe80::1"
    };

    for (String proxy : trustedProxies) {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.setRemoteAddr(proxy);
      request.addHeader("X-Forwarded-For", "unknown, \"[2001:db8::1]:8443\", 10.0.0.1");
      request.addHeader(
          "User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X) Version/18.0 Safari/605.1.15");

      AuthSessionClientMetadata metadata = resolver.resolve(request);
      assertThat(metadata.ipAddress()).isEqualTo("2001:db8::1");
      assertThat(metadata.deviceName()).isEqualTo("macOS / Safari");
    }
  }

  @Test
  void ignoresSpoofedForwardedHeadersWhenRemoteAddrIsUntrustedPublicIp() {
    String[] untrustedAddresses = {
      "198.51.100.77", "172.15.0.1", "172.32.0.1", "172.abc.0.1", "172.16", "2001:db8::77"
    };

    for (String remoteAddr : untrustedAddresses) {
      MockHttpServletRequest request = new MockHttpServletRequest();
      request.setRemoteAddr(remoteAddr);
      request.addHeader("X-Forwarded-For", "203.0.113.99");
      request.addHeader("Forwarded", "for=203.0.113.98");
      request.addHeader("X-Real-IP", "203.0.113.97");

      AuthSessionClientMetadata metadata = resolver.resolve(request);
      assertThat(metadata.ipAddress()).isEqualTo(remoteAddr);
    }
  }

  @Test
  void fallsBackAcrossForwardedHeadersAndParsesDeviceVariations() {
    MockHttpServletRequest forwardedRequest = new MockHttpServletRequest();
    forwardedRequest.setRemoteAddr("127.0.0.1");
    forwardedRequest.addHeader("X-Forwarded-For", " , unknown ");
    forwardedRequest.addHeader("Forwarded", "proto=https;for='198.51.100.44:443', for=10.0.0.1");
    forwardedRequest.addHeader(
        "User-Agent", "Mozilla/5.0 (iPad; CPU OS 18_0 like Mac OS X) CriOS/135.0 Chrome/135.0");
    AuthSessionClientMetadata forwardedMeta = resolver.resolve(forwardedRequest);
    assertThat(forwardedMeta.ipAddress()).isEqualTo("198.51.100.44");
    assertThat(forwardedMeta.deviceName()).isEqualTo("iPad / Chrome");

    MockHttpServletRequest realIpRequest = new MockHttpServletRequest();
    realIpRequest.setRemoteAddr("127.0.0.1");
    realIpRequest.addHeader("Forwarded", "proto=https;by=proxy, for=\"[\"");
    realIpRequest.addHeader("X-Real-IP", "192.0.2.55".repeat(10));
    realIpRequest.addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 15) Firefox/135.0");
    AuthSessionClientMetadata realIpMeta = resolver.resolve(realIpRequest);
    assertThat(realIpMeta.ipAddress()).hasSize(64);
    assertThat(realIpMeta.deviceName()).isEqualTo("Android / Firefox");

    MockHttpServletRequest remoteFallbackRequest = new MockHttpServletRequest();
    remoteFallbackRequest.setRemoteAddr("127.0.0.1");
    remoteFallbackRequest.addHeader("X-Real-IP", "   ");
    remoteFallbackRequest.addHeader("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) OPR/118.0");
    AuthSessionClientMetadata remoteMeta = resolver.resolve(remoteFallbackRequest);
    assertThat(remoteMeta.ipAddress()).isEqualTo("127.0.0.1");
    assertThat(remoteMeta.deviceName()).isEqualTo("Linux / Opera");

    MockHttpServletRequest osOnlyRequest = new MockHttpServletRequest();
    osOnlyRequest.setRemoteAddr("127.0.0.1");
    osOnlyRequest.addHeader("User-Agent", "CustomAgent (Windows NT 10.0)");
    assertThat(resolver.resolve(osOnlyRequest).deviceName()).isEqualTo("Windows");
    assertThat(resolver.resolve(osOnlyRequest).ipAddress()).isEqualTo("127.0.0.1");

    MockHttpServletRequest browserOnlyRequest = new MockHttpServletRequest();
    browserOnlyRequest.setRemoteAddr("127.0.0.1");
    browserOnlyRequest.addHeader("User-Agent", "CustomOS Edg/135.0");
    assertThat(resolver.resolve(browserOnlyRequest).deviceName()).isEqualTo("Edge");

    MockHttpServletRequest unknownAgentRequest = new MockHttpServletRequest();
    unknownAgentRequest.setRemoteAddr("127.0.0.1");
    unknownAgentRequest.addHeader("User-Agent", "   ");
    assertThat(resolver.resolve(unknownAgentRequest).deviceName()).isEqualTo("Unknown device");

    MockHttpServletRequest unrecognizedAgentRequest = new MockHttpServletRequest();
    unrecognizedAgentRequest.setRemoteAddr("127.0.0.1");
    unrecognizedAgentRequest.addHeader("User-Agent", "curl/8.7.1");
    assertThat(resolver.resolve(unrecognizedAgentRequest).deviceName()).isEqualTo("Unknown device");
  }
}
