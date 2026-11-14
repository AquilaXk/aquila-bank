package com.aquilabank.global.web.notification;

import com.aquilabank.domain.notification.model.NotificationSearchCursor;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

/** 검색 cursor는 44바이트 이진 헤더와 제한된 UTF-8 fingerprint를 Base64-url 인코딩한다 */
public final class NotificationSearchCursorCodec {

  private static final int HEADER_BYTES = (Long.BYTES + Integer.BYTES) * 3 + Long.BYTES;
  private static final int CREATED_AT_SEC_OFFSET = 0;
  private static final int CREATED_AT_NANO_OFFSET = 8;
  private static final int ID_OFFSET = 12;
  private static final int APPLIED_FROM_SEC_OFFSET = 20;
  private static final int APPLIED_FROM_NANO_OFFSET = 28;
  private static final int APPLIED_TO_SEC_OFFSET = 32;
  private static final int APPLIED_TO_NANO_OFFSET = 40;
  private static final int MAX_FINGERPRINT_BYTES = 320;
  private static final int MAX_ENCODED_LENGTH = 512;
  private static final int MAX_NANO = 999_999_999;
  private static final VarHandle LONG_VIEW =
      MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);
  private static final VarHandle INT_VIEW =
      MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.BIG_ENDIAN);
  private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

  private NotificationSearchCursorCodec() {}

  public static String encode(NotificationSearchCursor cursor) {
    if (cursor == null) {
      throw new IllegalArgumentException("cursor is required");
    }
    byte[] fingerprintBytes = cursor.filterFingerprint().getBytes(StandardCharsets.UTF_8);
    if (fingerprintBytes.length > MAX_FINGERPRINT_BYTES) {
      throw new IllegalArgumentException("filterFingerprint exceeds maximum length");
    }
    byte[] payload = new byte[HEADER_BYTES + fingerprintBytes.length];
    LONG_VIEW.set(payload, CREATED_AT_SEC_OFFSET, cursor.createdAt().getEpochSecond());
    INT_VIEW.set(payload, CREATED_AT_NANO_OFFSET, cursor.createdAt().getNano());
    LONG_VIEW.set(payload, ID_OFFSET, cursor.id());
    LONG_VIEW.set(payload, APPLIED_FROM_SEC_OFFSET, cursor.appliedFrom().getEpochSecond());
    INT_VIEW.set(payload, APPLIED_FROM_NANO_OFFSET, cursor.appliedFrom().getNano());
    LONG_VIEW.set(payload, APPLIED_TO_SEC_OFFSET, cursor.appliedTo().getEpochSecond());
    INT_VIEW.set(payload, APPLIED_TO_NANO_OFFSET, cursor.appliedTo().getNano());
    System.arraycopy(fingerprintBytes, 0, payload, HEADER_BYTES, fingerprintBytes.length);
    return URL_ENCODER.encodeToString(payload);
  }

  public static NotificationSearchCursor decode(String rawCursor) {
    if (rawCursor == null || rawCursor.isBlank() || rawCursor.length() > MAX_ENCODED_LENGTH) {
      throw new IllegalArgumentException("cursor format is invalid");
    }
    try {
      byte[] payload = URL_DECODER.decode(rawCursor);
      if (payload.length <= HEADER_BYTES || payload.length > HEADER_BYTES + MAX_FINGERPRINT_BYTES) {
        throw new IllegalArgumentException("cursor format is invalid");
      }
      long createdAtSec = (long) LONG_VIEW.get(payload, CREATED_AT_SEC_OFFSET);
      int createdAtNano = (int) INT_VIEW.get(payload, CREATED_AT_NANO_OFFSET);
      long id = (long) LONG_VIEW.get(payload, ID_OFFSET);
      long appliedFromSec = (long) LONG_VIEW.get(payload, APPLIED_FROM_SEC_OFFSET);
      int appliedFromNano = (int) INT_VIEW.get(payload, APPLIED_FROM_NANO_OFFSET);
      long appliedToSec = (long) LONG_VIEW.get(payload, APPLIED_TO_SEC_OFFSET);
      int appliedToNano = (int) INT_VIEW.get(payload, APPLIED_TO_NANO_OFFSET);
      if (!isValidNano(createdAtNano)
          || !isValidNano(appliedFromNano)
          || !isValidNano(appliedToNano)) {
        throw new IllegalArgumentException("cursor format is invalid");
      }
      String filterFingerprint =
          new String(payload, HEADER_BYTES, payload.length - HEADER_BYTES, StandardCharsets.UTF_8);
      return new NotificationSearchCursor(
          Instant.ofEpochSecond(createdAtSec, createdAtNano),
          id,
          Instant.ofEpochSecond(appliedFromSec, appliedFromNano),
          Instant.ofEpochSecond(appliedToSec, appliedToNano),
          filterFingerprint);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException("cursor format is invalid", ex);
    }
  }

  private static boolean isValidNano(int nano) {
    return nano >= 0 && nano <= MAX_NANO;
  }
}
