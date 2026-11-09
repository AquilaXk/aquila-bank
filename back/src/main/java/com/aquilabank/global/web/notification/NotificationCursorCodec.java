package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.NotificationCursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/** keyset cursor를 HTTP query string 으로 옮길 때만 쓰는 transport codec */
public final class NotificationCursorCodec {

  private static final char DELIMITER_CHAR = '|';

  private NotificationCursorCodec() {}

  public static String encode(NotificationCursor cursor) {
    String payload = cursor.createdAt().toString() + DELIMITER_CHAR + cursor.id();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  public static NotificationCursor decode(String rawCursor) {
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(rawCursor), StandardCharsets.UTF_8);
      int sep = decoded.indexOf(DELIMITER_CHAR);
      if (sep <= 0 || sep == decoded.length() - 1) {
        throw new IllegalArgumentException("cursor format is invalid");
      }
      return new NotificationCursor(
          Instant.parse(decoded.substring(0, sep)), Long.parseLong(decoded.substring(sep + 1)));
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("cursor format is invalid", ex);
    }
  }
}
