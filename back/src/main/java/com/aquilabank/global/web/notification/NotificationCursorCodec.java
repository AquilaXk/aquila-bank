package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.NotificationCursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/** keyset cursor를 HTTP query string 으로 옮길 때만 쓰는 transport codec */
public final class NotificationCursorCodec {

  private static final String DELIMITER = "|";

  private NotificationCursorCodec() {}

  public static String encode(NotificationCursor cursor) {
    String payload = cursor.createdAt().toString() + DELIMITER + cursor.id();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  public static NotificationCursor decode(String rawCursor) {
    try {
      String decoded =
          new String(
              Base64.getUrlDecoder().decode(rawCursor.getBytes(StandardCharsets.UTF_8)),
              StandardCharsets.UTF_8);
      String[] tokens = decoded.split("\\|", 2);
      if (tokens.length != 2) {
        throw new IllegalArgumentException("cursor format is invalid");
      }
      return new NotificationCursor(Instant.parse(tokens[0]), Long.parseLong(tokens[1]));
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("cursor format is invalid", ex);
    }
  }
}
