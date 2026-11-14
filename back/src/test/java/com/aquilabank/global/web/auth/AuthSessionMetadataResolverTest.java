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
    assertThat(realIpMeta.ipAddress()).isEqualTo("127.0.0.1");
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

  @Test
  void scansForwardedHeadersRightToLeftAndPreventsLeftSpoofing() {
    MockHttpServletRequest xffSpoofed = new MockHttpServletRequest();
    xffSpoofed.setRemoteAddr("10.0.0.20");
    xffSpoofed.addHeader("X-Forwarded-For", "198.51.100.99, 203.0.113.5, 10.0.0.1");
    assertThat(resolver.resolve(xffSpoofed).ipAddress()).isEqualTo("203.0.113.5");

    MockHttpServletRequest xffAllTrusted = new MockHttpServletRequest();
    xffAllTrusted.setRemoteAddr("10.0.0.20");
    xffAllTrusted.addHeader("X-Forwarded-For", "10.0.0.2, 10.0.0.1");
    assertThat(resolver.resolve(xffAllTrusted).ipAddress()).isEqualTo("10.0.0.2");

    MockHttpServletRequest xffWithUnknownGap = new MockHttpServletRequest();
    xffWithUnknownGap.setRemoteAddr("10.0.0.20");
    xffWithUnknownGap.addHeader("X-Forwarded-For", "198.51.100.99, unknown, 10.0.0.1");
    assertThat(resolver.resolve(xffWithUnknownGap).ipAddress()).isEqualTo("10.0.0.1");

    MockHttpServletRequest xffWithMalformedGap = new MockHttpServletRequest();
    xffWithMalformedGap.setRemoteAddr("10.0.0.20");
    xffWithMalformedGap.addHeader("X-Forwarded-For", "198.51.100.99, bad_host, 10.0.0.1");
    assertThat(resolver.resolve(xffWithMalformedGap).ipAddress()).isEqualTo("10.0.0.20");

    MockHttpServletRequest forwardedSpoofed = new MockHttpServletRequest();
    forwardedSpoofed.setRemoteAddr("10.0.0.20");
    forwardedSpoofed.addHeader(
        "Forwarded", "for=198.51.100.99, for=203.0.113.6;proto=https, for=10.0.0.1");
    assertThat(resolver.resolve(forwardedSpoofed).ipAddress()).isEqualTo("203.0.113.6");
  }

  @Test
  void rejectsControlCharsMalformedTokensAndCapsOversizedHeaders() {
    MockHttpServletRequest crlfXff = new MockHttpServletRequest();
    crlfXff.setRemoteAddr("127.0.0.1");
    crlfXff.addHeader("X-Forwarded-For", "203.0.113.10\r\nX-Injected: true");
    crlfXff.addHeader("User-Agent", "Mozilla/5.0 (iPhone)\r\nX-Fake: Chrome/135.0");
    AuthSessionClientMetadata crlfMeta = resolver.resolve(crlfXff);
    assertThat(crlfMeta.ipAddress()).isEqualTo("127.0.0.1");
    assertThat(crlfMeta.deviceName()).isEqualTo("Unknown device");

    MockHttpServletRequest crlfForwarded = new MockHttpServletRequest();
    crlfForwarded.setRemoteAddr("127.0.0.1");
    crlfForwarded.addHeader("Forwarded", "for=203.0.113.10\n");
    crlfForwarded.addHeader("X-Real-IP", "203.0.113.11\t");
    assertThat(resolver.resolve(crlfForwarded).ipAddress()).isEqualTo("127.0.0.1");

    MockHttpServletRequest invalidIps = new MockHttpServletRequest();
    invalidIps.setRemoteAddr("127.0.0.1");
    invalidIps.addHeader(
        "X-Forwarded-For", "999.0.0.1, localhost, 2001:db8::zz, [2001:db8::1]:abc");
    invalidIps.addHeader("X-Real-IP", "203.0.113.10:abc");
    assertThat(resolver.resolve(invalidIps).ipAddress()).isEqualTo("127.0.0.1");

    MockHttpServletRequest ipv4Port = new MockHttpServletRequest();
    ipv4Port.setRemoteAddr("127.0.0.1");
    ipv4Port.addHeader("X-Real-IP", "198.51.100.25:443");
    assertThat(resolver.resolve(ipv4Port).ipAddress()).isEqualTo("198.51.100.25");

    MockHttpServletRequest oversizedUserAgent = new MockHttpServletRequest();
    oversizedUserAgent.setRemoteAddr("127.0.0.1");
    oversizedUserAgent.addHeader("User-Agent", "A".repeat(600) + " (Windows NT 10.0) Chrome/135.0");
    assertThat(resolver.resolve(oversizedUserAgent).deviceName()).isEqualTo("Unknown device");

    MockHttpServletRequest badRemote = new MockHttpServletRequest();
    badRemote.setRemoteAddr("evil\r\naddr");
    assertThat(resolver.resolve(badRemote).ipAddress()).isEqualTo("unknown");

    MockHttpServletRequest macOperaLeadingSpace = new MockHttpServletRequest();
    macOperaLeadingSpace.setRemoteAddr("127.0.0.1");
    macOperaLeadingSpace.addHeader("X-Forwarded-For", "2001:db8::2");
    macOperaLeadingSpace.addHeader("User-Agent", "   Macintosh; Opera/9.80");
    AuthSessionClientMetadata macOperaMeta = resolver.resolve(macOperaLeadingSpace);
    assertThat(macOperaMeta.ipAddress()).isEqualTo("2001:db8::2");
    assertThat(macOperaMeta.deviceName()).isEqualTo("macOS / Opera");

    MockHttpServletRequest forwardedAndEdgeMalformed = new MockHttpServletRequest();
    forwardedAndEdgeMalformed.setRemoteAddr("10.0.0.1");
    forwardedAndEdgeMalformed.addHeader("X-Forwarded-For", "192.0.2.1,".repeat(300));
    forwardedAndEdgeMalformed.addHeader("Forwarded", "for=198.51.100.1, for=unknown, for=10.0.0.2");
    forwardedAndEdgeMalformed.addHeader("X-Real-IP", "unknown");
    assertThat(resolver.resolve(forwardedAndEdgeMalformed).ipAddress()).isEqualTo("10.0.0.2");

    MockHttpServletRequest badIpv6AndPort = new MockHttpServletRequest();
    badIpv6AndPort.setRemoteAddr("1".repeat(80));
    badIpv6AndPort.addHeader("X-Real-IP", "[2001:한글::1]");
    assertThat(resolver.resolve(badIpv6AndPort).ipAddress()).isEqualTo("unknown");

    MockHttpServletRequest badFormats = new MockHttpServletRequest();
    badFormats.setRemoteAddr("127.0.0.1");
    badFormats.addHeader("X-Forwarded-For", "foo:80");
    badFormats.addHeader("Forwarded", "for=\"[2001:db8::1]:99999\"");
    badFormats.addHeader("X-Real-IP", "192.168..1");
    assertThat(resolver.resolve(badFormats).ipAddress()).isEqualTo("127.0.0.1");

    MockHttpServletRequest badBracketSuffix = new MockHttpServletRequest();
    badBracketSuffix.setRemoteAddr("127.0.0.1");
    badBracketSuffix.addHeader("X-Real-IP", "[2001:db8::1]extra");
    assertThat(resolver.resolve(badBracketSuffix).ipAddress()).isEqualTo("127.0.0.1");
  }
}
