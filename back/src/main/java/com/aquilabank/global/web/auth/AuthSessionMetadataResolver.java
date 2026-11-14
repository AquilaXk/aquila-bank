package com.aquilabank.global.web.auth;

import com.aquilabank.domain.auth.model.AuthSessionClientMetadata;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/** auth session 식별에 필요한 device/ip 메타데이터를 request에서 경량 추출합니다. */
@Component
public class AuthSessionMetadataResolver {

  private static final int USER_AGENT_MAX_LENGTH = 512;
  private static final int IP_ADDRESS_MAX_LENGTH = 64;
  private static final int FORWARDED_HEADER_MAX_LENGTH = 2048;

  private static final byte IP_MASK_DIGIT = 0x01;
  private static final byte IP_MASK_HEX = 0x02;
  private static final byte IP_MASK_DOT = 0x04;
  private static final byte IP_MASK_COLON = 0x08;
  private static final byte[] IP_CHAR_LUT = buildIpCharLut();

  public AuthSessionClientMetadata resolve(HttpServletRequest request) {
    String userAgent = request.getHeader("User-Agent");
    return new AuthSessionClientMetadata(resolveDeviceName(userAgent), resolveIpAddress(request));
  }

  private static byte[] buildIpCharLut() {
    byte[] table = new byte[128];
    for (int c = '0'; c <= '9'; c++) {
      table[c] = (byte) (IP_MASK_DIGIT | IP_MASK_HEX);
    }
    for (int c = 'a'; c <= 'f'; c++) {
      table[c] = IP_MASK_HEX;
    }
    for (int c = 'A'; c <= 'F'; c++) {
      table[c] = IP_MASK_HEX;
    }
    table['.'] = IP_MASK_DOT;
    table[':'] = IP_MASK_COLON;
    return table;
  }

  private String resolveDeviceName(String userAgent) {
    if (userAgent == null || userAgent.isBlank() || hasControlChar(userAgent)) {
      return "Unknown device";
    }
    int scanStart = 0;
    int scanEnd = userAgent.length();
    while (scanStart < scanEnd && userAgent.charAt(scanStart) == ' ') {
      scanStart++;
    }
    int limit = Math.min(scanEnd, scanStart + USER_AGENT_MAX_LENGTH);
    String operatingSystem = resolveOperatingSystem(userAgent, scanStart, limit);
    String browser = resolveBrowser(userAgent, scanStart, limit);
    if (operatingSystem != null && browser != null) {
      return operatingSystem + " / " + browser;
    }
    if (operatingSystem != null) {
      return operatingSystem;
    }
    if (browser != null) {
      return browser;
    }
    return "Unknown device";
  }

  private String resolveOperatingSystem(String userAgent, int start, int limit) {
    if (containsAsciiIgnoreCase(userAgent, start, limit, "iphone")) {
      return "iPhone";
    }
    if (containsAsciiIgnoreCase(userAgent, start, limit, "ipad")) {
      return "iPad";
    }
    if (containsAsciiIgnoreCase(userAgent, start, limit, "android")) {
      return "Android";
    }
    if (containsAsciiIgnoreCase(userAgent, start, limit, "windows")) {
      return "Windows";
    }
    if (containsAsciiIgnoreCase(userAgent, start, limit, "mac os x")
        || containsAsciiIgnoreCase(userAgent, start, limit, "macintosh")) {
      return "macOS";
    }
    if (containsAsciiIgnoreCase(userAgent, start, limit, "linux")) {
      return "Linux";
    }
    return null;
  }

  private String resolveBrowser(String userAgent, int start, int limit) {
    if (containsAsciiIgnoreCase(userAgent, start, limit, "edg/")) {
      return "Edge";
    }
    if (containsAsciiIgnoreCase(userAgent, start, limit, "opr/")
        || containsAsciiIgnoreCase(userAgent, start, limit, "opera")) {
      return "Opera";
    }
    if (containsAsciiIgnoreCase(userAgent, start, limit, "chrome/")) {
      return "Chrome";
    }
    if (containsAsciiIgnoreCase(userAgent, start, limit, "firefox/")) {
      return "Firefox";
    }
    if (containsAsciiIgnoreCase(userAgent, start, limit, "safari/")) {
      return "Safari";
    }
    return null;
  }

  private String resolveIpAddress(HttpServletRequest request) {
    String rawRemoteAddr = request.getRemoteAddr();
    boolean missingRemoteAddr = rawRemoteAddr == null || rawRemoteAddr.isBlank();
    String remoteAddr = missingRemoteAddr ? null : normalizeRemoteAddr(rawRemoteAddr);
    if (missingRemoteAddr || (remoteAddr != null && isTrustedProxy(remoteAddr))) {
      // 신뢰 가능한 프록시에서 온 요청일 때만 우측 비신뢰 프록시 직전 주소를 역방향 단일 스캔으로 추출합니다.
      String ipAddress = parseXForwardedForRightToLeft(request.getHeader("X-Forwarded-For"));
      if (ipAddress != null) {
        return ipAddress;
      }
      ipAddress = parseForwardedRightToLeft(request.getHeader("Forwarded"));
      if (ipAddress != null) {
        return ipAddress;
      }
      ipAddress = normalizeXRealIp(request.getHeader("X-Real-IP"));
      if (ipAddress != null) {
        return ipAddress;
      }
    }
    return remoteAddr != null ? remoteAddr : "unknown";
  }

  private String normalizeXRealIp(String headerValue) {
    if (headerValue == null
        || headerValue.isBlank()
        || headerValue.length() > FORWARDED_HEADER_MAX_LENGTH
        || hasControlChar(headerValue)) {
      return null;
    }
    int[] bounds = trimAndUnquoteBounds(headerValue, 0, headerValue.length());
    int s = bounds[0];
    int e = bounds[1];
    if (s >= e || equalsAsciiIgnoreCase(headerValue, s, e, "unknown")) {
      return null;
    }
    return normalizeUnwrappedIpSlice(headerValue, s, e);
  }

  private String normalizeRemoteAddr(String value) {
    if (hasControlChar(value)) {
      return null;
    }
    int start = trimLeft(value, 0, value.length());
    int end = trimRight(value, start, value.length());
    if (end - start > IP_ADDRESS_MAX_LENGTH) {
      return null;
    }
    return value.substring(start, end);
  }

  private boolean isTrustedProxy(String normalized) {
    if (equalsAsciiIgnoreCase(normalized, "localhost")
        || "::1".equals(normalized)
        || "0:0:0:0:0:0:0:1".equals(normalized)
        || normalized.startsWith("127.")
        || normalized.startsWith("10.")
        || normalized.startsWith("192.168.")
        || startsWithAsciiIgnoreCase(normalized, "fc")
        || startsWithAsciiIgnoreCase(normalized, "fd")
        || startsWithAsciiIgnoreCase(normalized, "fe80:")) {
      return true;
    }
    if (normalized.startsWith("172.")) {
      int secondDot = normalized.indexOf('.', 4);
      if (secondDot > 4) {
        int secondOctet = parseBoundedInt(normalized, 4, secondDot);
        return secondOctet >= 16 && secondOctet <= 31;
      }
    }
    return false;
  }

  private String parseXForwardedForRightToLeft(String headerValue) {
    if (headerValue == null
        || headerValue.isBlank()
        || headerValue.length() > FORWARDED_HEADER_MAX_LENGTH
        || hasControlChar(headerValue)) {
      return null;
    }
    String fallbackTrusted = null;
    int end = headerValue.length();
    while (end > 0) {
      int comma = headerValue.lastIndexOf(',', end - 1);
      int start = comma + 1;
      int[] bounds = trimAndUnquoteBounds(headerValue, start, end);
      int tokenStart = bounds[0];
      int tokenEnd = bounds[1];
      if (tokenStart >= tokenEnd
          || equalsAsciiIgnoreCase(headerValue, tokenStart, tokenEnd, "unknown")) {
        if (fallbackTrusted != null) {
          return fallbackTrusted;
        }
      } else {
        String normalized = normalizeUnwrappedIpSlice(headerValue, tokenStart, tokenEnd);
        if (normalized == null) {
          return null;
        }
        if (!isTrustedProxy(normalized)) {
          return normalized;
        }
        fallbackTrusted = normalized;
      }
      end = comma;
    }
    return fallbackTrusted;
  }

  private String parseForwardedRightToLeft(String headerValue) {
    if (headerValue == null
        || headerValue.isBlank()
        || headerValue.length() > FORWARDED_HEADER_MAX_LENGTH
        || hasControlChar(headerValue)) {
      return null;
    }
    String fallbackTrusted = null;
    int entryEnd = headerValue.length();
    while (entryEnd > 0) {
      int comma = headerValue.lastIndexOf(',', entryEnd - 1);
      int segStart = comma + 1;
      while (segStart < entryEnd) {
        int semi = headerValue.indexOf(';', segStart);
        int segEnd = (semi < 0 || semi >= entryEnd) ? entryEnd : semi;
        int trimmedStart = trimLeft(headerValue, segStart, segEnd);
        int trimmedEnd = trimRight(headerValue, trimmedStart, segEnd);
        if (trimmedEnd - trimmedStart >= 4
            && headerValue.regionMatches(true, trimmedStart, "for=", 0, 4)) {
          int[] bounds = trimAndUnquoteBounds(headerValue, trimmedStart + 4, trimmedEnd);
          int valStart = bounds[0];
          int valEnd = bounds[1];
          if (valStart >= valEnd
              || equalsAsciiIgnoreCase(headerValue, valStart, valEnd, "unknown")) {
            if (fallbackTrusted != null) {
              return fallbackTrusted;
            }
          } else {
            String normalized = normalizeUnwrappedIpSlice(headerValue, valStart, valEnd);
            if (normalized == null) {
              return null;
            }
            if (!isTrustedProxy(normalized)) {
              return normalized;
            }
            fallbackTrusted = normalized;
          }
        }
        segStart = segEnd + 1;
      }
      entryEnd = comma;
    }
    return fallbackTrusted;
  }

  private String normalizeUnwrappedIpSlice(String value, int start, int end) {
    if (value.charAt(start) == '[') {
      int closingBracket = value.indexOf(']', start + 1);
      if (closingBracket <= start + 1 || closingBracket >= end) {
        return null;
      }
      if (closingBracket + 1 < end
          && (value.charAt(closingBracket + 1) != ':'
              || !isValidPort(value, closingBracket + 2, end))) {
        return null;
      }
      int ipLen = closingBracket - (start + 1);
      if (ipLen > IP_ADDRESS_MAX_LENGTH || !isValidIpv6Slice(value, start + 1, closingBracket)) {
        return null;
      }
      return value.substring(start + 1, closingBracket);
    }

    int colonCount = 0;
    int dotCount = 0;
    int firstColon = -1;
    for (int i = start; i < end; i++) {
      char ch = value.charAt(i);
      if (ch >= 128 || IP_CHAR_LUT[ch] == 0) {
        return null;
      }
      if (ch == ':') {
        if (colonCount == 0) {
          firstColon = i;
        }
        colonCount++;
      } else if (ch == '.') {
        dotCount++;
      }
    }

    if (colonCount == 0) {
      if (end - start > IP_ADDRESS_MAX_LENGTH || !isValidIpv4(value, start, end)) {
        return null;
      }
      return value.substring(start, end);
    }
    if (colonCount == 1 && dotCount > 0) {
      if (firstColon - start > IP_ADDRESS_MAX_LENGTH
          || !isValidIpv4(value, start, firstColon)
          || !isValidPort(value, firstColon + 1, end)) {
        return null;
      }
      return value.substring(start, firstColon);
    }
    if (colonCount >= 2
        && end - start <= IP_ADDRESS_MAX_LENGTH
        && isValidIpv6Slice(value, start, end)) {
      return value.substring(start, end);
    }
    return null;
  }

  private static boolean isValidIpv4(String value, int start, int end) {
    int octetVal = 0;
    int octetDigits = 0;
    int dotCount = 0;
    for (int i = start; i < end; i++) {
      char ch = value.charAt(i);
      if (ch == '.') {
        if (octetDigits == 0) {
          return false;
        }
        dotCount++;
        octetVal = 0;
        octetDigits = 0;
      } else if ((IP_CHAR_LUT[ch] & IP_MASK_DIGIT) != 0) {
        octetVal = octetVal * 10 + (ch - '0');
        octetDigits++;
        if (octetVal > 255) {
          return false;
        }
      } else {
        return false;
      }
    }
    return dotCount == 3 && octetDigits > 0;
  }

  private static boolean isValidIpv6Slice(String value, int start, int end) {
    int colonCount = 0;
    for (int i = start; i < end; i++) {
      char ch = value.charAt(i);
      if (ch >= 128 || (IP_CHAR_LUT[ch] & (IP_MASK_HEX | IP_MASK_COLON | IP_MASK_DOT)) == 0) {
        return false;
      }
      if (ch == ':') {
        colonCount++;
      }
    }
    return colonCount >= 2 && colonCount <= 7;
  }

  private static boolean isValidPort(String value, int start, int end) {
    int len = end - start;
    if (len < 1 || len > 5) {
      return false;
    }
    int port = 0;
    for (int i = start; i < end; i++) {
      char ch = value.charAt(i);
      if (ch >= 128 || (IP_CHAR_LUT[ch] & IP_MASK_DIGIT) == 0) {
        return false;
      }
      port = port * 10 + (ch - '0');
    }
    return port >= 1 && port <= 65535;
  }

  private static int parseBoundedInt(String value, int start, int end) {
    int result = 0;
    for (int i = start; i < end; i++) {
      char ch = value.charAt(i);
      if (ch < '0' || ch > '9') {
        return -1;
      }
      result = result * 10 + (ch - '0');
    }
    return result;
  }

  private static boolean hasControlChar(String value) {
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (ch < 0x20 || ch == 0x7F) {
        return true;
      }
    }
    return false;
  }

  private static int[] trimAndUnquoteBounds(String value, int start, int end) {
    int s = trimLeft(value, start, end);
    int e = trimRight(value, s, end);
    if (e - s >= 2) {
      char first = value.charAt(s);
      char last = value.charAt(e - 1);
      if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
        s = trimLeft(value, s + 1, e - 1);
        e = trimRight(value, s, e - 1);
      }
    }
    return new int[] {s, e};
  }

  private static int trimLeft(String value, int start, int end) {
    int s = start;
    while (s < end && value.charAt(s) == ' ') {
      s++;
    }
    return s;
  }

  private static int trimRight(String value, int start, int end) {
    int e = end;
    while (e > start && value.charAt(e - 1) == ' ') {
      e--;
    }
    return e;
  }

  private static boolean containsAsciiIgnoreCase(
      String haystack, int start, int limit, String lowerNeedle) {
    int needleLen = lowerNeedle.length();
    if (limit - start < needleLen) {
      return false;
    }
    for (int i = start; i <= limit - needleLen; i++) {
      if (equalsAsciiIgnoreCase(haystack, i, i + needleLen, lowerNeedle)) {
        return true;
      }
    }
    return false;
  }

  private static boolean startsWithAsciiIgnoreCase(String value, String lowerPrefix) {
    int prefixLen = lowerPrefix.length();
    return value.length() >= prefixLen && equalsAsciiIgnoreCase(value, 0, prefixLen, lowerPrefix);
  }

  private static boolean equalsAsciiIgnoreCase(String value, String lowerTarget) {
    return value.length() == lowerTarget.length()
        && equalsAsciiIgnoreCase(value, 0, value.length(), lowerTarget);
  }

  private static boolean equalsAsciiIgnoreCase(
      String value, int start, int end, String lowerTarget) {
    int len = lowerTarget.length();
    if (end - start != len) {
      return false;
    }
    for (int i = 0; i < len; i++) {
      char ch = value.charAt(start + i);
      char folded = (ch >= 'A' && ch <= 'Z') ? (char) (ch | 0x20) : ch;
      if (folded != lowerTarget.charAt(i)) {
        return false;
      }
    }
    return true;
  }
}
