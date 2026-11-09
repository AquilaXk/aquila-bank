package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.NotificationSearchCursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/** 검색 cursor는 query string transport용으로만 Base64-url 인코딩한다 */
public final class NotificationSearchCursorCodec {

  private static final char DELIMITER_CHAR = '|';

  private NotificationSearchCursorCodec() {}

  public static String encode(NotificationSearchCursor cursor) {
    String payload =
        cursor.createdAt().toString()
            + DELIMITER_CHAR
            + cursor.id()
            + DELIMITER_CHAR
            + cursor.appliedFrom()
            + DELIMITER_CHAR
            + cursor.appliedTo()
            + DELIMITER_CHAR
            + cursor.filterFingerprint();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  public static NotificationSearchCursor decode(String rawCursor) {
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(rawCursor), StandardCharsets.UTF_8);
      int sep1 = requireDelimiter(decoded, 0);
      int sep2 = requireDelimiter(decoded, sep1 + 1);
      int sep3 = requireDelimiter(decoded, sep2 + 1);
      int sep4 = requireDelimiter(decoded, sep3 + 1);
      return new NotificationSearchCursor(
          Instant.parse(decoded.substring(0, sep1)),
          Long.parseLong(decoded.substring(sep1 + 1, sep2)),
          Instant.parse(decoded.substring(sep2 + 1, sep3)),
          Instant.parse(decoded.substring(sep3 + 1, sep4)),
          decoded.substring(sep4 + 1));
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("cursor format is invalid", ex);
    }
  }

  private static int requireDelimiter(String value, int fromIndex) {
    int index = value.indexOf(DELIMITER_CHAR, fromIndex);
    if (index < 0) {
      throw new IllegalArgumentException("cursor format is invalid");
    }
    return index;
  }
}
