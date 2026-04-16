package com.aquilabank.global.web.transaction;

import com.aquilabank.domain.transaction.model.TransactionCursor;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/** keyset cursor의 HTTP transport codec */
public final class TransactionCursorCodec {

  private static final String DELIMITER = "|";

  private TransactionCursorCodec() {}

  public static String encode(TransactionCursor cursor) {
    // Base64는 cursor를 opaque하게 전달하기 위한 transport encoding일 뿐
    String payload = cursor.bookedAt().toString() + DELIMITER + cursor.id();
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
  }

  public static TransactionCursor decode(String rawCursor) {
    try {
      String decoded =
          new String(
              Base64.getUrlDecoder().decode(rawCursor.getBytes(StandardCharsets.UTF_8)),
              StandardCharsets.UTF_8);
      String[] tokens = decoded.split("\\|", 2);
      if (tokens.length != 2) {
        throw new IllegalArgumentException("cursor format is invalid");
      }
      return new TransactionCursor(Instant.parse(tokens[0]), Long.parseLong(tokens[1]));
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("cursor format is invalid", ex);
    }
  }
}
