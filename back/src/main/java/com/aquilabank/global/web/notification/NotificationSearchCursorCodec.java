package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.NotificationSearchCursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/** 검색 cursor는 query string transport용으로만 Base64-url 인코딩한다 */
public final class NotificationSearchCursorCodec {

  private static final String DELIMITER = "|";

  private NotificationSearchCursorCodec() {}

  public static String encode(NotificationSearchCursor cursor) {
    String payload =
        cursor.createdAt()
            + DELIMITER
            + cursor.id()
            + DELIMITER
            + cursor.appliedFrom()
            + DELIMITER
            + cursor.appliedTo()
            + DELIMITER
            + cursor.filterFingerprint();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  public static NotificationSearchCursor decode(String rawCursor) {
    try {
      String decoded =
          new String(
              Base64.getUrlDecoder().decode(rawCursor.getBytes(StandardCharsets.UTF_8)),
              StandardCharsets.UTF_8);
      String[] tokens = decoded.split("\\|", 5);
      if (tokens.length != 5) {
        throw new IllegalArgumentException("cursor format is invalid");
      }
      return new NotificationSearchCursor(
          Instant.parse(tokens[0]),
          Long.parseLong(tokens[1]),
          Instant.parse(tokens[2]),
          Instant.parse(tokens[3]),
          tokens[4]);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("cursor format is invalid", ex);
    }
  }
}
