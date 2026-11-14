package com.aquilabank.global.persistence.transaction;

/** pg_stat_activity에서 query shape별 실행 추적을 남기되 PreparedStatement 캐시를 깨지 않는 정적 SQL comment */
final class TransactionReadSqlTraceComment {

  private static final int MAX_TOKEN_LENGTH = 96;

  static String current(String shape) {
    // 요청별 requestId를 SQL 본문에 넣으면 PGJDBC/JdbcTemplate parsedSqlCache 캐시가 무력화되므로 정적 shape만 남깁니다.
    return "/* queryShape=" + sanitize(shape) + " */" + System.lineSeparator();
  }

  private static String sanitize(String value) {
    if (value == null || value.isBlank()) {
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
