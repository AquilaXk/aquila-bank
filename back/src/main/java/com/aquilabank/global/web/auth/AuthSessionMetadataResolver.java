package com.aquilabank.global.web.auth;

import com.aquilabank.domain.auth.model.AuthSessionClientMetadata;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** auth session 식별에 필요한 device/ip 메타데이터를 request에서 경량 추출합니다. */
@Component
public class AuthSessionMetadataResolver {

  private static final int DEVICE_NAME_MAX_LENGTH = 120;
  private static final int IP_ADDRESS_MAX_LENGTH = 64;

  public AuthSessionClientMetadata resolve(HttpServletRequest request) {
    String userAgent = trimHeader(request.getHeader("User-Agent"));
    return new AuthSessionClientMetadata(resolveDeviceName(userAgent), resolveIpAddress(request));
  }

  private String resolveDeviceName(String userAgent) {
    if (userAgent == null) {
      return "Unknown device";
    }
    String normalizedUserAgent = userAgent.toLowerCase(Locale.ROOT);
    String operatingSystem = resolveOperatingSystem(normalizedUserAgent);
    String browser = resolveBrowser(normalizedUserAgent);
    String deviceName;
    if (operatingSystem != null && browser != null) {
      deviceName = operatingSystem + " / " + browser;
    } else if (operatingSystem != null) {
      deviceName = operatingSystem;
    } else if (browser != null) {
      deviceName = browser;
    } else {
      deviceName = "Unknown device";
    }
    return trimToLength(deviceName, DEVICE_NAME_MAX_LENGTH);
  }

  private String resolveOperatingSystem(String userAgent) {
    if (userAgent.contains("iphone")) {
      return "iPhone";
    }
    if (userAgent.contains("ipad")) {
      return "iPad";
    }
    if (userAgent.contains("android")) {
      return "Android";
    }
    if (userAgent.contains("windows")) {
      return "Windows";
    }
    if (userAgent.contains("mac os x") || userAgent.contains("macintosh")) {
      return "macOS";
    }
    if (userAgent.contains("linux")) {
      return "Linux";
    }
    return null;
  }

  private String resolveBrowser(String userAgent) {
    if (userAgent.contains("edg/")) {
      return "Edge";
    }
    if (userAgent.contains("opr/") || userAgent.contains("opera")) {
      return "Opera";
    }
    if (userAgent.contains("chrome/")) {
      return "Chrome";
    }
    if (userAgent.contains("firefox/")) {
      return "Firefox";
    }
    if (userAgent.contains("safari/")) {
      return "Safari";
    }
    return null;
  }

  private String resolveIpAddress(HttpServletRequest request) {
    // reverse proxy 뒤 실제 client IP를 우선 잡으려고 forwarded 계열 헤더부터 확인합니다.
    String ipAddress = firstIpToken(request.getHeader("X-Forwarded-For"));
    if (ipAddress != null) {
      return trimToLength(ipAddress, IP_ADDRESS_MAX_LENGTH);
    }
    ipAddress = forwardedHeaderIp(request.getHeader("Forwarded"));
    if (ipAddress != null) {
      return trimToLength(ipAddress, IP_ADDRESS_MAX_LENGTH);
    }
    ipAddress = normalizeIpToken(request.getHeader("X-Real-IP"));
    if (ipAddress != null) {
      return trimToLength(ipAddress, IP_ADDRESS_MAX_LENGTH);
    }
    return trimToLength(normalizeIpToken(request.getRemoteAddr()), IP_ADDRESS_MAX_LENGTH);
  }

  private String firstIpToken(String headerValue) {
    if (headerValue == null || headerValue.isBlank()) {
      return null;
    }
    String[] tokens = headerValue.split(",");
    for (String token : tokens) {
      String normalized = normalizeIpToken(token);
      if (normalized != null) {
        return normalized;
      }
    }
    return null;
  }

  private String forwardedHeaderIp(String headerValue) {
    if (headerValue == null || headerValue.isBlank()) {
      return null;
    }
    String[] entries = headerValue.split(",");
    for (String entry : entries) {
      String[] segments = entry.split(";");
      for (String segment : segments) {
        String trimmed = segment.trim();
        if (!trimmed.regionMatches(true, 0, "for=", 0, 4)) {
          continue;
        }
        String normalized = normalizeIpToken(trimmed.substring(4));
        if (normalized != null) {
          return normalized;
        }
      }
    }
    return null;
  }

  private String normalizeIpToken(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String trimmed = value.trim();
    if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
        || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
      trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
    }
    if (trimmed.isBlank() || "unknown".equalsIgnoreCase(trimmed)) {
      return null;
    }
    if (trimmed.startsWith("[")) {
      int closingBracketIndex = trimmed.indexOf(']');
      if (closingBracketIndex > 0) {
        return trimmed.substring(1, closingBracketIndex);
      }
      return null;
    }
    if (trimmed.chars().filter(ch -> ch == ':').count() == 1 && trimmed.contains(".")) {
      return trimmed.substring(0, trimmed.indexOf(':'));
    }
    return trimmed;
  }

  private String trimHeader(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  private String trimToLength(String value, int maxLength) {
    if (value == null) {
      return null;
    }
    if (value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }
}
