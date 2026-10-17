package com.aquilabank.global.persistence.transaction;

import com.aquilabank.global.web.RequestTraceContext;

/** pg_stat_activity에서 transaction read 요청을 requestId로 역추적하기 위한 SQL comment */
final class TransactionReadSqlTraceComment {

  private static final int MAX_TOKEN_LENGTH = 96;

  static String current(String shape) {
    String requestId = RequestTraceContext.currentRequestId().orElse("unknown");
    return "/* requestId="
        + sanitize(requestId)
        + " queryShape="
        + sanitize(shape)
        + " */"
        + System.lineSeparator();
  }

  private static String sanitize(String value) {
    if (value.isBlank()) {
      return "unknown";
    }
    int limit = Math.min(value.length(), MAX_TOKEN_LENGTH);
    char[] chars = null;
    for (int i = 0; i < limit; i++) {
      char ch = value.charAt(i);
      if (!isAllowedTraceChar(ch)) {
        if (chars == null) {
          chars = value.substring(0, limit).toCharArray();
        }
        chars[i] = '_';
      }
    }
    if (chars != null) {
      return new String(chars);
    }
    return value.length() > MAX_TOKEN_LENGTH ? value.substring(0, MAX_TOKEN_LENGTH) : value;
  }

  private static boolean isAllowedTraceChar(char ch) {
    return (ch >= 'A' && ch <= 'Z')
        || (ch >= 'a' && ch <= 'z')
        || (ch >= '0' && ch <= '9')
        || ch == '_'
        || ch == '.'
        || ch == ':'
        || ch == '-';
  }
}
