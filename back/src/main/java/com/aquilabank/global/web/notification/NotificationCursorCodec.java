package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.NotificationCursor;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.Base64;

/** keyset cursor를 20바이트 고정 이진 페이로드(createdAt sec/nano + id)로 직렬화하는 transport codec */
public final class NotificationCursorCodec {

  private static final int PAYLOAD_BYTES = Long.BYTES + Integer.BYTES + Long.BYTES;
  private static final int MAX_ENCODED_LENGTH = 32;
  private static final int MAX_NANO = 999_999_999;
  private static final VarHandle LONG_VIEW =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);
  private static final VarHandle INT_VIEW =
      MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

  private NotificationCursorCodec() {}

  public static String encode(NotificationCursor cursor) {
    if (cursor == null) {
      throw new IllegalArgumentException("cursor is required");
    }
    byte[] payload = new byte[PAYLOAD_BYTES];
    LONG_VIEW.set(payload, 0, cursor.createdAt().getEpochSecond());
    INT_VIEW.set(payload, Long.BYTES, cursor.createdAt().getNano());
    LONG_VIEW.set(payload, Long.BYTES + Integer.BYTES, cursor.id());
    return URL_ENCODER.encodeToString(payload);
  }

  public static NotificationCursor decode(String rawCursor) {
    if (rawCursor == null || rawCursor.isBlank() || rawCursor.length() > MAX_ENCODED_LENGTH) {
      throw new IllegalArgumentException("cursor format is invalid");
    }
    try {
      byte[] payload = URL_DECODER.decode(rawCursor);
      if (payload.length != PAYLOAD_BYTES) {
        throw new IllegalArgumentException("cursor format is invalid");
      }
      long epochSecond = (long) LONG_VIEW.get(payload, 0);
      int nano = (int) INT_VIEW.get(payload, Long.BYTES);
      long id = (long) LONG_VIEW.get(payload, Long.BYTES + Integer.BYTES);
      if (nano < 0 || nano > MAX_NANO) {
        throw new IllegalArgumentException("cursor format is invalid");
      }
      return new NotificationCursor(Instant.ofEpochSecond(epochSecond, nano), id);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("cursor format is invalid", ex);
    }
  }
}
